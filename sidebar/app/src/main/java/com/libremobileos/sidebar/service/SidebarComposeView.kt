package com.libremobileos.sidebar.service

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import com.android.settingslib.spa.framework.compose.rememberDrawablePainter
import com.libremobileos.sidebar.R
import com.libremobileos.sidebar.app.SidebarApplication
import com.libremobileos.sidebar.bean.AppInfo

@Composable
fun SidebarComposeView(
    viewModel: ServiceViewModel,
    launchApp: (AppInfo) -> Unit,
    closeSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activePopup by remember { mutableStateOf<PopupState?>(null) }
    val bubbleSupported = remember { viewModel.isBubbleSupported }
    val sharedPrefs = context.getSharedPreferences(SidebarApplication.CONFIG, Context.MODE_PRIVATE)

    val columnCount = sharedPrefs.getInt("sidebar_columns", 1)
    val iconSize = sharedPrefs.getInt("sidebar_icon_size", 40)
    val iconPadding = sharedPrefs.getInt("sidebar_icon_padding", 7)
    val columnSpacing = sharedPrefs.getInt("sidebar_column_spacing", 4)
    val cornerRadius = sharedPrefs.getFloat("sidebar_corner_radius", 24f)
    val backgroundTransparency = sharedPrefs.getFloat("sidebar_background_transparency", 0.80f)
    val showShadow = sharedPrefs.getBoolean("sidebar_show_shadow", true)

    val sidebarAppList by viewModel.sidebarAppListFlow.collectAsState()

    val allItems = buildList {
        add(SidebarItem.AllApps(viewModel.allAppActivity))
        addAll(sidebarAppList.map { SidebarItem.App(it) })
        add(SidebarItem.Settings)
    }

    var cardModifier = modifier

    if (showShadow) {
        cardModifier = cardModifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(cornerRadius.dp)
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = backgroundTransparency
            )
        ),
        shape = RoundedCornerShape(cornerRadius.dp),
        modifier = cardModifier
    ) {
        if (columnCount == 1) {
            LazyColumn(
                contentPadding = PaddingValues(4.dp)
            ) {
                items(allItems) { item ->
                    SidebarItemView(
                        item = item,
                        iconSize = iconSize,
                        iconPadding = iconPadding,
                        viewModel = viewModel,
                        launchApp = launchApp,
                        closeSidebar = closeSidebar,
                        onLongPressed = { popup -> activePopup = popup }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(columnSpacing.dp),
                verticalArrangement = Arrangement.spacedBy(columnSpacing.dp),
                modifier = Modifier.width(
                    (
                        (iconSize + iconPadding * 2) * columnCount +
                        columnSpacing * (columnCount - 1) +
                        8
                    ).dp
                )
            ) {
                items(allItems) { item ->
                    SidebarItemView(
                        item = item,
                        iconSize = iconSize,
                        iconPadding = iconPadding,
                        viewModel = viewModel,
                        launchApp = launchApp,
                        closeSidebar = closeSidebar,
                        onLongPressed = { popup -> activePopup = popup }
                    )
                }
            }
        }
    }

    activePopup?.let { popup ->
        LaunchModePopup(
            anchorBounds = popup.anchorBounds,
            bubbleSupported = bubbleSupported,
            onDismiss = { activePopup = null },
            onLaunchFreeform = {
                launchApp(popup.appInfo)
                activePopup = null
                closeSidebar()
            },
            onLaunchFullscreen = {
                val intent: Intent? = context.packageManager
                    .getLaunchIntentForPackage(popup.appInfo.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                activePopup = null
                closeSidebar()
            },
            onLaunchBubble = {
                viewModel.launchAsBubble(popup.appInfo.packageName, popup.appInfo.activityName)
                activePopup = null
                closeSidebar()
            }
        )
    }
}

private data class PopupState(
    val appInfo: AppInfo,
    val anchorBounds: Rect
)

@Composable
private fun SidebarItemView(
    item: SidebarItem,
    iconSize: Int,
    iconPadding: Int,
    viewModel: ServiceViewModel,
    launchApp: (AppInfo) -> Unit,
    closeSidebar: () -> Unit,
    onLongPressed: (PopupState) -> Unit
) {
    Box(
        modifier = Modifier.padding(iconPadding.dp),
        contentAlignment = Alignment.Center
    ) {
        when (item) {
            is SidebarItem.AllApps -> {
                Icon(
                    imageVector = Icons.Outlined.Widgets,
                    contentDescription = item.appInfo.label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size((iconSize - 4).dp)
                        .clickable { launchApp(item.appInfo) }
                )
            }
            is SidebarItem.App -> {
                var anchorBounds by remember { mutableStateOf<Rect?>(null) }
                Image(
                    painter = rememberDrawablePainter(item.appInfo.icon),
                    contentDescription = item.appInfo.label,
                    modifier = Modifier
                        .size(iconSize.dp)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            val size = coords.size
                            anchorBounds = Rect(
                                pos.x.toInt(),
                                pos.y.toInt(),
                                (pos.x + size.width).toInt(),
                                (pos.y + size.height).toInt()
                            )
                        }
                        .combinedClickable(
                            onClick = { launchApp(item.appInfo) },
                            onLongClick = {
                                anchorBounds?.let { bounds ->
                                    onLongPressed(PopupState(item.appInfo, bounds))
                                }
                            }
                        )
                )
            }
            is SidebarItem.Settings -> {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.sidebar_settings_description),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size((iconSize - 4).dp)
                        .clickable {
                            viewModel.openSidebarSettings()
                            closeSidebar()
                        }
                )
            }
        }
    }
}

@Composable
private fun LaunchModePopup(
    anchorBounds: Rect,
    bubbleSupported: Boolean,
    onDismiss: () -> Unit,
    onLaunchFreeform: () -> Unit,
    onLaunchFullscreen: () -> Unit,
    onLaunchBubble: () -> Unit
) {
    val anchorCenterY = (anchorBounds.top + anchorBounds.bottom) / 2

    Popup(
        offset = IntOffset(anchorBounds.right + 8, anchorCenterY - 78),
        onDismissRequest = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .width(180.dp)
                .zIndex(10f)
        ) {
            PopupMenuRow(
                icon = Icons.Outlined.OpenInNew,
                label = stringResource(R.string.sidebar_launch_freeform),
                isFirst = true,
                isLast = !bubbleSupported,
                onClick = onLaunchFreeform
            )
            PopupMenuRow(
                icon = Icons.Outlined.OpenInFull,
                label = stringResource(R.string.sidebar_launch_fullscreen),
                isFirst = false,
                isLast = !bubbleSupported,
                onClick = onLaunchFullscreen
            )
            if (bubbleSupported) {
                PopupMenuRow(
                    icon = Icons.Outlined.BubbleChart,
                    label = stringResource(R.string.sidebar_launch_bubble),
                    isFirst = false,
                    isLast = true,
                    onClick = onLaunchBubble
                )
            }
        }
    }
}

@Composable
private fun PopupMenuRow(
    icon: ImageVector,
    label: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val topRadius = if (isFirst) 16.dp else 4.dp
    val bottomRadius = if (isLast) 16.dp else 4.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(
            topStart = topRadius, topEnd = topRadius,
            bottomStart = bottomRadius, bottomEnd = bottomRadius
        ),
        color = MaterialTheme.colorScheme.surfaceBright,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

private sealed class SidebarItem {
    data class AllApps(val appInfo: AppInfo) : SidebarItem()
    data class App(val appInfo: AppInfo) : SidebarItem()
    data object Settings : SidebarItem()
}
