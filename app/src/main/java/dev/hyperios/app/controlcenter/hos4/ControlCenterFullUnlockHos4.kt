package dev.hyperios.app.controlcenter.hos4

import android.content.Context
import android.view.ViewGroup
import dev.hyperios.app.controlcenter.ControlCenterFullUnlock
import dev.hyperios.app.controlcenter.TileGroup

/**
 * Compatibility forwarding shim for HyperOS 4.
 * Delegates all operations to unified ControlCenterFullUnlock with native HOS 4 gating.
 */
object ControlCenterFullUnlockHos4 {
    var sDistributor: Any?
        get() = ControlCenterFullUnlock.sDistributor
        set(value) { ControlCenterFullUnlock.sDistributor = value }

    var sRightAdapter: Any?
        get() = ControlCenterFullUnlock.sRightAdapter
        set(value) { ControlCenterFullUnlock.sRightAdapter = value }

    var sLeftAdapter: Any?
        get() = ControlCenterFullUnlock.sLeftAdapter
        set(value) { ControlCenterFullUnlock.sLeftAdapter = value }

    fun createLandscapeDividerViewHolder(parent: ViewGroup, classLoader: ClassLoader): Any? =
        ControlCenterFullUnlock.createLandscapeDividerViewHolder(parent, classLoader)

    fun reorganizeContentMap(adapter: Any, classLoader: ClassLoader) =
        ControlCenterFullUnlock.reorganizeContentMap(adapter, classLoader)

    fun buildAndSetContentMap(adapter: Any, classLoader: ClassLoader) =
        ControlCenterFullUnlock.buildAndSetContentMap(adapter, classLoader)

    fun onGroupCreated(groupId: String, specs: List<String>, context: Context?) =
        ControlCenterFullUnlock.onGroupCreated(groupId, specs, context)

    fun onGroupsDisassembled(removedGroups: List<TileGroup>, context: Context?) =
        ControlCenterFullUnlock.onGroupsDisassembled(removedGroups, context)

    fun resetOrder(context: Context?) =
        ControlCenterFullUnlock.resetOrder(context)
}
