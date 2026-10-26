package com.iamokumu.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.setBackgroundColor(0xFF000000.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        hideBars()
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        web.evaluateJavascript("window.refreshApps && window.refreshApps()", null)
    }

    override fun onBackPressed() { /* stay on the home screen */ }

    private fun hideBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    inner class Bridge {
        private fun installed(): Map<String, Pair<String, Drawable>> {
            val pm = packageManager
            val out = LinkedHashMap<String, Pair<String, Drawable>>()
            for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (ri in pm.queryIntentActivities(i, 0)) {
                    val p = ri.activityInfo.packageName
                    if (!out.containsKey(p)) out[p] = Pair(ri.loadLabel(pm).toString(), ri.loadIcon(pm))
                }
            }
            return out
        }

        private fun png(d: Drawable): String {
            val s = 160
            val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            d.setBounds(0, 0, s, s); d.draw(c)
            val o = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.PNG, 100, o)
            return "data:image/png;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
        }

        /** config: [{name, pkgs:[...], labels:[...]}]  ->  [{name, pkg|null, icon|null}] */
        @JavascriptInterface
        fun resolve(config: String): String {
            val apps = installed()
            val res = JSONArray()
            val cfg = JSONArray(config)
            for (k in 0 until cfg.length()) {
                val e = cfg.getJSONObject(k)
                var hit: String? = null
                val pkgs = e.optJSONArray("pkgs") ?: JSONArray()
                for (j in 0 until pkgs.length()) if (apps.containsKey(pkgs.getString(j))) { hit = pkgs.getString(j); break }
                if (hit == null) {
                    val labels = e.optJSONArray("labels") ?: JSONArray()
                    loop@ for ((p, v) in apps) for (j in 0 until labels.length())
                        if (v.first.lowercase().contains(labels.getString(j).lowercase())) { hit = p; break@loop }
                }
                val o = JSONObject().put("name", e.getString("name"))
                if (hit != null) o.put("pkg", hit).put("icon", png(apps[hit]!!.second))
                res.put(o)
            }
            return res.toString()
        }

        @JavascriptInterface
        fun launch(pkg: String) {
            val pm = packageManager
            val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i) }
        }
    }
}
