package app.autoledger.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object UiTextExtractor {
  private const val TAG = "AutoLedger/Extract"

  /**
   * Walk the AccessibilityNode tree and collect visible-ish text.
   * This is a best-effort heuristic for payment success pages.
   */
  fun extract(root: AccessibilityNodeInfo?): String {
    if (root == null) return ""
    val sb = StringBuilder(4096)
    var count = 0

    fun walk(node: AccessibilityNodeInfo?, depth: Int) {
      if (node == null) return
      if (count > 2000) return

      val t = node.text?.toString()?.trim().orEmpty()
      val d = node.contentDescription?.toString()?.trim().orEmpty()
      val cls = node.className?.toString()?.trim().orEmpty()

      if (t.isNotBlank()) {
        sb.append(t).append('\n')
        count++
      }
      if (d.isNotBlank() && d != t) {
        sb.append(d).append('\n')
        count++
      }

      // Recurse
      val n = node.childCount
      for (i in 0 until n) {
        walk(node.getChild(i), depth + 1)
      }
    }

    try {
      walk(root, 0)
    } catch (e: Exception) {
      Log.e(TAG, "extract failed", e)
    }

    val out = sb.toString().trim()
    Log.i(TAG, "extracted chars=${out.length} lines=${out.lines().size}")
    return out
  }
}
