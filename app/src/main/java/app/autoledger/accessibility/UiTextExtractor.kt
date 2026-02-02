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
    if (root == null) {
      Log.w(TAG, "extract: root is null")
      return ""
    }

    val rootPkg = root.packageName?.toString().orEmpty()
    val rootClass = root.className?.toString().orEmpty()
    val rootChildCount = root.childCount
    Log.i(TAG, "extract: rootPkg=$rootPkg rootClass=$rootClass rootChildCount=$rootChildCount windowId=${root.windowId}")

    val sb = StringBuilder(4096)
    var count = 0
    var nullChildCount = 0
    var maxDepthReached = 0

    fun walk(node: AccessibilityNodeInfo?, depth: Int) {
      if (node == null) { nullChildCount++; return }
      if (count > 2000) return
      if (depth > maxDepthReached) maxDepthReached = depth

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
      Log.e(TAG, "extract failed for pkg=$rootPkg", e)
    }

    val out = sb.toString().trim()
    Log.i(TAG, "extracted pkg=$rootPkg chars=${out.length} lines=${out.lines().size} nodeCount=$count nullChildren=$nullChildCount maxDepth=$maxDepthReached")
    if (out.isEmpty()) {
      Log.w(TAG, "extract returned EMPTY for pkg=$rootPkg (rootChildCount=$rootChildCount). WeChat/secure apps may block accessibility tree access.")
    }
    return out
  }
}
