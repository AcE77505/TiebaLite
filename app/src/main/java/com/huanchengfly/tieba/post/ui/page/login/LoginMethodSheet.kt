package com.huanchengfly.tieba.post.ui.page.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.widgets.compose.ListMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.dialogs.AnyPopDialog
import com.huanchengfly.tieba.post.ui.widgets.compose.dialogs.AnyPopDialogProperties
import com.huanchengfly.tieba.post.ui.widgets.compose.dialogs.DirectionState

/**
 * Bottom sheet that lets the user pick a login method (web or BDUSS).
 */
@Composable
fun LoginMethodSheet(
    onDismiss: () -> Unit,
    onWebLogin: () -> Unit,
    onBdussLogin: () -> Unit,
) {
    AnyPopDialog(
        onDismiss = onDismiss,
        properties = AnyPopDialogProperties(
            direction = DirectionState.BOTTOM,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))

                ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                    Text(
                        text = stringResource(R.string.title_login_method),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 16.dp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()

                ListMenuItem(
                    icon = Icons.Outlined.Language,
                    text = stringResource(R.string.button_web_login),
                    onClick = {
                        onDismiss()
                        onWebLogin()
                    },
                )

                ListMenuItem(
                    icon = Icons.Outlined.Key,
                    text = stringResource(R.string.button_bduss_login),
                    onClick = {
                        onDismiss()
                        onBdussLogin()
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
