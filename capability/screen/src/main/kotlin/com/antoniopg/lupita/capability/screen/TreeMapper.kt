package com.antoniopg.lupita.capability.screen

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.antoniopg.lupita.core.model.SelectionRect
import com.antoniopg.lupita.core.model.UiNode
import com.antoniopg.lupita.core.model.intersects

/**
 * Traduce el arbol de accesibilidad a [UiNode] leyendo SOLO lo que toca la region: un subarbol que no la
 * cruza ni se recorre. Con tope de nodos y de profundidad para que una pantalla enorme o rara no cuelgue el
 * servicio. Los campos de contrasena llegan marcados (los quita la puerta de privacidad, que es quien decide).
 */
internal object TreeMapper {
    const val MAX_NODES = 3000
    const val MAX_DEPTH = 80

    class Result(val roots: List<UiNode>, val truncated: Boolean)

    fun map(root: AccessibilityNodeInfo, region: SelectionRect): Result {
        val budget = intArrayOf(MAX_NODES)
        val node = convert(root, region, 0, budget)
        return Result(listOfNotNull(node), truncated = budget[0] <= 0)
    }

    private fun convert(info: AccessibilityNodeInfo, region: SelectionRect, depth: Int, budget: IntArray): UiNode? {
        if (budget[0] <= 0 || depth > MAX_DEPTH) return null
        val rect = Rect().also(info::getBoundsInScreen)
        val bounds = SelectionRect(rect.left, rect.top, maxOf(rect.right, rect.left), maxOf(rect.bottom, rect.top))
        if (!bounds.intersects(region)) return null
        budget[0]--

        val children = buildList {
            for (i in 0 until info.childCount) {
                val child = info.getChild(i) ?: continue
                convert(child, region, depth + 1, budget)?.let(::add)
            }
        }
        return UiNode(
            bounds = bounds,
            className = info.className?.toString(),
            text = info.text?.toString(),
            contentDescription = info.contentDescription?.toString(),
            viewId = info.viewIdResourceName,
            isPassword = info.isPassword,
            isEditable = info.isEditable,
            isClickable = info.isClickable,
            contentWithheld = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                info.isAccessibilityDataSensitive,
            children = children,
        )
    }
}
