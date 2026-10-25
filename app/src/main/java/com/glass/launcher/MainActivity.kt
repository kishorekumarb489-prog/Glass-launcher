package com.glass.launcher

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class AppItem(
    val label: String,
    val pkg: String,
    val launch: Intent,
    val icon: Drawable
)

class MainActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var grid: RecyclerView
    private lateinit var dock: LinearLayout
    private lateinit var search: EditText
    private lateinit var drop: View
    private lateinit var adapter: AppAdapter
    private var allApps: List<AppItem> = emptyList()

    private lateinit var swipeDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        grid = findViewById(R.id.grid)
        dock = findViewById(R.id.dock)
        search = findViewById(R.id.search)
        drop = findViewById(R.id.drop)

        // Keep content clear of status bar and navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        // Blurs the wallpaper behind the launcher (Android 12+ on devices that support it)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.setBackgroundBlurRadius(25)
            } catch (_: Throwable) {
            }
        }

        adapter = AppAdapter(
            onClick = { item, view -> launchWithDrop(item.launch, view) },
            onLongClick = { item, view -> showMenu(item, view) }
        )
        grid.layoutManager = GridLayoutManager(this, 4)
        grid.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString().orEmpty())
            }
        })

        // Swipe down on the home screen to open the notification shade
        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float
            ): Boolean {
                val start = e1 ?: return false
                val dy = e2.y - start.y
                if (dy > 150 && vy > 1200 && !grid.canScrollVertically(-1)) {
                    expandStatusBar()
                    return true
                }
                return false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed: reset search and scroll to top
        search.setText("")
        grid.scrollToPosition(0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher should stay on home screen
        search.setText("")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun loadApps() {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        allApps = pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val pkg = it.activityInfo.packageName
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(pkg, it.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                AppItem(it.loadLabel(pm).toString(), pkg, launch, it.loadIcon(pm))
            }
            .sortedBy { it.label.lowercase() }
        filter(search.text?.toString().orEmpty())
        buildDock()
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        adapter.submit(if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) })
    }

    private fun buildDock() {
        dock.removeAllViews()
        val pm = packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
        )
        for (intent in intents) {
            val info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: continue
            val launch = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val view = LayoutInflater.from(this).inflate(R.layout.item_app, dock, false)
            view.findViewById<TextView>(R.id.label).visibility = View.GONE
            styleIcon(view.findViewById(R.id.icon), info.loadIcon(pm))
            view.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            attachPressEffect(view)
            view.setOnClickListener { launchWithDrop(launch, view) }
            dock.addView(view)
        }
    }

    // ---- Icon style: iOS-like rounded squircle ----
    private fun styleIcon(iv: ImageView, icon: Drawable) {
        iv.setImageDrawable(icon)
        iv.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, v.width * 0.225f)
            }
        }
        iv.clipToOutline = true
    }

    // ---- Droplet press effect: squish down, bounce back ----
    @SuppressLint("ClickableViewAccessibility")
    private fun attachPressEffect(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.86f).scaleY(0.86f).setDuration(120).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(OvershootInterpolator(3.5f)).start()
            }
            false
        }
    }

    // ---- Glass droplet expands from the icon, then the app opens ----
    private fun launchWithDrop(intent: Intent, from: View) {
        val loc = IntArray(2)
        val rootLoc = IntArray(2)
        from.getLocationInWindow(loc)
        root.getLocationInWindow(rootLoc)
        val cx = loc[0] - rootLoc[0] + from.width / 2f
        val cy = loc[1] - rootLoc[1] + from.height / 2f

        drop.x = cx - drop.width / 2f
        drop.y = cy - drop.height / 2f
        drop.scaleX = 0.4f
        drop.scaleY = 0.4f
        drop.alpha = 0.9f
        drop.visibility = View.VISIBLE
        drop.animate()
            .scaleX(7f).scaleY(7f).alpha(0f)
            .setDuration(380)
            .withEndAction { drop.visibility = View.GONE }
            .start()

        startAppSafely(intent)
    }

    private fun startAppSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }

    private fun showMenu(item: AppItem, anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add("App info")
        menu.menu.add("Uninstall")
        menu.setOnMenuItemClickListener {
            val uri = Uri.parse("package:${item.pkg}")
            if (it.title == "App info") {
                startAppSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            } else {
                startAppSafely(Intent(Intent.ACTION_DELETE, uri))
            }
            true
        }
        menu.show()
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandStatusBar() {
        try {
            val service = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(service)
        } catch (_: Throwable) {
        }
    }

    // ---- Adapter ----
    inner class AppAdapter(
        private val onClick: (AppItem, View) -> Unit,
        private val onLongClick: (AppItem, View) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        private var items: List<AppItem> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<AppItem>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val label: TextView = v.findViewById(R.id.label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val item = items[position]
            h.label.text = item.label
            styleIcon(h.icon, item.icon)
            attachPressEffect(h.itemView)
            h.itemView.setOnClickListener { onClick(item, h.icon) }
            h.itemView.setOnLongClickListener {
                onLongClick(item, h.icon)
                true
            }
        }

        override fun getItemCount() = items.size
    }
}
