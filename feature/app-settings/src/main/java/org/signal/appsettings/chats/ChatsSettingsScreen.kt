/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.chats

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.appsettings.backups.BackupCreationProgress
import org.signal.appsettings.backups.BackupCreationProgressRow
import org.signal.core.ui.biometrics.rememberBiometricsAuthentication
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts

@VisibleForTesting
object ChatsSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_GENERATE_LINK_PREVIEWS = "row-generate-link-previews"
  const val ROW_SEND_LINK_PREVIEW_IMAGES = "row-send-link-preview-images"
  const val ROW_KEEP_VIEW_ONCE_MESSAGES = "row-keep-view-once-messages"
  const val ROW_IGNORE_REMOTE_DELETE = "row-ignore-remote-delete"
  const val ROW_IGNORE_ADMIN_DELETE = "row-ignore-admin-delete"
  const val ROW_DELETE_MEDIA_ONLY = "row-delete-media-only"
  const val ROW_WHO_CAN_ADD_YOU_TO_GROUPS = "row-who-can-add-you-to-groups"
  const val DIALOG_WHO_CAN_ADD_YOU_TO_GROUPS = "dialog-who-can-add-you-to-groups"
  const val OPTION_WHO_CAN_ADD_ANYONE = "option-who-can-add-anyone"
  const val OPTION_WHO_CAN_ADD_NONBLOCKED = "option-who-can-add-nonblocked"
  const val OPTION_WHO_CAN_ADD_ONLYCONTACTS = "option-who-can-add-onlycontacts"
  const val OPTION_WHO_CAN_ADD_ONLYSYSTEMCONTACTS = "option-who-can-add-onlysystemcontacts"
  const val OPTION_WHO_CAN_ADD_NOBODY = "option-who-can-add-nobody"
  const val ROW_AUTOPLAY_STICKERS_AND_GIFS = "row-autoplay-stickers-and-gifs"
  const val ROW_USE_ADDRESS_BOOK = "row-use-address-book"
  const val ROW_KEEP_MUTED_CHATS_ARCHIVED = "row-keep-muted-chats-archived"
  const val ROW_CHAT_FOLDERS = "row-chat-folders"
  const val ROW_EXPORT_CHAT_HISTORY = "row-export-chat-history"
  const val ROW_EXPORT_PROGRESS = "row-export-progress"
  const val ROW_USE_SYSTEM_EMOJI = "row-use-system-emoji"
  const val ROW_ENTER_KEY_SENDS = "row-enter-key-sends"
  const val DIALOG_CHOOSE_A_FOLDER = "dialog-choose-a-folder"
  const val DIALOG_EXPORT_COMPLETE = "dialog-export-complete"
}

@Composable
fun ChatsSettingsScreen(
  state: ChatsSettingsState,
  onEvent: (ChatsSettingsEvents) -> Unit
) {
  LifecycleResumeEffect(Unit) {
    onEvent(ChatsSettingsEvents.Refresh)
    onPauseOrDispose {}
  }

  var showWhoCanAddYouToGroupsDialog by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val authenticationFailedMessage = stringResource(R.string.ChatsSettingsFragment__authentication_failed)
  val exportBiometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(R.string.ChatsSettingsFragment__unlock_to_export_chat_history),
    onAuthenticationFailed = {
      coroutineScope.launch {
        snackbarHostState.showSnackbar(authenticationFailedMessage)
      }
    }
  )

  Scaffolds.Settings(
    title = stringResource(R.string.preferences_chats__chats),
    onNavigationClick = { onEvent(ChatsSettingsEvents.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.ChatsSettingsScreen__go_back),
    snackbarHost = {
      Snackbars.Host(snackbarHostState)
    },
    expandableTitle = true
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues,
      modifier = Modifier.testTag(ChatsSettingsTestTags.SCROLLER)
    ) {
      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_GENERATE_LINK_PREVIEWS),
          text = stringResource(R.string.preferences__generate_link_previews),
          label = stringResource(R.string.preferences__retrieve_link_previews_from_websites_for_messages),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.generateLinkPreviews,
          onCheckChanged = { onEvent(ChatsSettingsEvents.GenerateLinkPreviewsChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_SEND_LINK_PREVIEW_IMAGES),
          text = stringResource(R.string.ChatsSettingsScreen__send_link_preview_images),
          label = stringResource(R.string.ChatsSettingsScreen__send_link_preview_images_description),
          enabled = state.isRegisteredAndUpToDate() && state.generateLinkPreviews,
          checked = state.sendLinkPreviewImages,
          onCheckChanged = { onEvent(ChatsSettingsEvents.SendLinkPreviewImagesChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_AUTOPLAY_STICKERS_AND_GIFS),
          text = stringResource(R.string.ChatsSettingsFragment__autoplay_stickers_and_gifs),
          label = stringResource(R.string.ChatsSettingsFragment__when_turned_on_stickers_and_gifs_will_play_automatically),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.shouldAutoplayStickersAndGifs,
          onCheckChanged = { onEvent(ChatsSettingsEvents.AutoplayStickersAndGifsChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_USE_ADDRESS_BOOK),
          text = stringResource(R.string.preferences__pref_use_address_book_photos),
          label = stringResource(R.string.preferences__display_contact_photos_from_your_address_book_if_available),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.useAddressBook,
          onCheckChanged = { onEvent(ChatsSettingsEvents.UseAddressBookChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_KEEP_VIEW_ONCE_MESSAGES),
          text = stringResource(R.string.ChatsSettingsScreen__keep_view_once_messages),
          label = stringResource(R.string.ChatsSettingsScreen__keep_view_once_messages_description),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.keepViewOnceMessages,
          onCheckChanged = { onEvent(ChatsSettingsEvents.KeepViewOnceMessagesChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_IGNORE_REMOTE_DELETE),
          text = stringResource(R.string.ChatsSettingsScreen__ignore_remote_delete),
          label = stringResource(R.string.ChatsSettingsScreen__ignore_remote_delete_description),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.ignoreRemoteDelete,
          onCheckChanged = { onEvent(ChatsSettingsEvents.IgnoreRemoteDeleteChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_IGNORE_ADMIN_DELETE),
          text = stringResource(R.string.ChatsSettingsScreen__ignore_admin_delete),
          label = stringResource(R.string.ChatsSettingsScreen__ignore_admin_delete_description),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.ignoreAdminDelete,
          onCheckChanged = { onEvent(ChatsSettingsEvents.IgnoreAdminDeleteChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_DELETE_MEDIA_ONLY),
          text = stringResource(R.string.ChatsSettingsScreen__delete_media_only),
          label = stringResource(R.string.ChatsSettingsScreen__delete_media_only_description),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.deleteMediaOnly,
          onCheckChanged = { onEvent(ChatsSettingsEvents.DeleteMediaOnlyChanged(it)) }
        )
      }

      item {
        Rows.TextRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_WHO_CAN_ADD_YOU_TO_GROUPS),
          text = stringResource(R.string.ChatsSettingsScreen__who_can_add_you_to_groups),
          label = stringResource(whoCanAddYouToGroupsLabel(state.whoCanAddYouToGroups)),
          enabled = state.isRegisteredAndUpToDate(),
          onClick = { showWhoCanAddYouToGroupsDialog = true }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_KEEP_MUTED_CHATS_ARCHIVED),
          text = stringResource(R.string.preferences__pref_keep_muted_chats_archived),
          label = stringResource(R.string.preferences__muted_chats_that_are_archived_will_remain_archived),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.keepMutedChatsArchived,
          onCheckChanged = { onEvent(ChatsSettingsEvents.KeepMutedChatsArchivedChanged(it)) }
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.ChatsSettingsFragment__chat_folders))
      }

      if (state.folderCount == 1) {
        item {
          Rows.TextRow(
            modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_CHAT_FOLDERS),
            text = stringResource(R.string.ChatsSettingsFragment__add_chat_folder),
            enabled = state.isRegisteredAndUpToDate(),
            onClick = { onEvent(ChatsSettingsEvents.ChatFoldersClicked) }
          )
        }
      } else {
        item {
          Rows.TextRow(
            modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_CHAT_FOLDERS),
            text = stringResource(R.string.ChatsSettingsFragment__add_edit_chat_folder),
            label = pluralStringResource(R.plurals.ChatsSettingsFragment__d_folder, state.folderCount, state.folderCount),
            enabled = state.isRegisteredAndUpToDate(),
            onClick = { onEvent(ChatsSettingsEvents.ChatFoldersClicked) }
          )
        }
      }

      if (state.isPlaintextExportEnabled) {
        item {
          Dividers.Default()
        }

        if (state.plaintextExportProgress.isIdle) {
          item(key = "export_chat_history_row") {
            Rows.TextRow(
              modifier = Modifier
                .animateItem()
                .testTag(ChatsSettingsTestTags.ROW_EXPORT_CHAT_HISTORY),
              text = stringResource(R.string.ChatsSettingsFragment__export_chat_history),
              label = stringResource(R.string.ChatsSettingsFragment__export_chat_history_label),
              onClick = {
                exportBiometrics.withBiometricsAuthentication {
                  onEvent(ChatsSettingsEvents.ExportChatHistoryAuthenticated)
                }
              }
            )
          }
        } else {
          item(key = "export_chat_history_progress") {
            BackupCreationProgressRow(
              modifier = Modifier
                .animateItem()
                .testTag(ChatsSettingsTestTags.ROW_EXPORT_PROGRESS),
              progress = state.plaintextExportProgress,
              isRemote = false,
              onCancel = { onEvent(ChatsSettingsEvents.CancelInFlightExportClicked) }
            )
          }
        }
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.ChatsSettingsFragment__keyboard))
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_USE_SYSTEM_EMOJI),
          text = stringResource(R.string.preferences_advanced__use_system_emoji),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.useSystemEmoji,
          onCheckChanged = { onEvent(ChatsSettingsEvents.UseSystemEmojiChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_ENTER_KEY_SENDS),
          text = stringResource(R.string.ChatsSettingsFragment__send_with_enter),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.enterKeySends,
          onCheckChanged = { onEvent(ChatsSettingsEvents.EnterKeySendsChanged(it)) }
        )
      }
    }
  }

  if (state.isPlaintextExportEnabled) {
    ChatExportDialogs(
      state = state.chatExportState,
      onEvent = onEvent
    )
  }

  if (showWhoCanAddYouToGroupsDialog) {
    WhoCanAddYouToGroupsDialog(
      selected = state.whoCanAddYouToGroups,
      onSelect = {
        onEvent(ChatsSettingsEvents.WhoCanAddYouToGroupsChanged(it))
        showWhoCanAddYouToGroupsDialog = false
      },
      onDismiss = { showWhoCanAddYouToGroupsDialog = false }
    )
  }
}

private fun whoCanAddYouToGroupsLabel(value: String): Int {
  return when (value) {
    "anyone" -> R.string.ChatsSettingsScreen__who_can_add_anyone
    "onlycontacts" -> R.string.ChatsSettingsScreen__who_can_add_onlycontacts
    "onlysystemcontacts" -> R.string.ChatsSettingsScreen__who_can_add_onlysystemcontacts
    "nobody" -> R.string.ChatsSettingsScreen__who_can_add_nobody
    else -> R.string.ChatsSettingsScreen__who_can_add_nonblocked
  }
}

@Composable
private fun WhoCanAddYouToGroupsDialog(
  selected: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag(ChatsSettingsTestTags.DIALOG_WHO_CAN_ADD_YOU_TO_GROUPS),
    title = { Text(text = stringResource(R.string.ChatsSettingsScreen__who_can_add_you_to_groups)) },
    text = {
      Column {
        Text(text = stringResource(R.string.ChatsSettingsScreen__who_can_add_you_to_groups_description))
        WhoCanAddOption("anyone", ChatsSettingsTestTags.OPTION_WHO_CAN_ADD_ANYONE, selected, onSelect)
        WhoCanAddOption("nonblocked", ChatsSettingsTestTags.OPTION_WHO_CAN_ADD_NONBLOCKED, selected, onSelect)
        WhoCanAddOption("onlycontacts", ChatsSettingsTestTags.OPTION_WHO_CAN_ADD_ONLYCONTACTS, selected, onSelect)
        WhoCanAddOption("onlysystemcontacts", ChatsSettingsTestTags.OPTION_WHO_CAN_ADD_ONLYSYSTEMCONTACTS, selected, onSelect)
        WhoCanAddOption("nobody", ChatsSettingsTestTags.OPTION_WHO_CAN_ADD_NOBODY, selected, onSelect)
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(android.R.string.cancel))
      }
    }
  )
}

@Composable
private fun WhoCanAddOption(
  value: String,
  testTag: String,
  selected: String,
  onSelect: (String) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onSelect(value) }
      .padding(vertical = 8.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    RadioButton(selected = selected == value, onClick = { onSelect(value) })
    Text(text = stringResource(whoCanAddYouToGroupsLabel(value)))
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ChatsSettingsScreenPreview() {
  ChatsSettingsScreen(
    state = chatsSettingsPreviewState,
    onEvent = {}
  )
}

internal val chatsSettingsPreviewState = ChatsSettingsState(
  generateLinkPreviews = true,
  sendLinkPreviewImages = true,
  useAddressBook = true,
  keepMutedChatsArchived = true,
  useSystemEmoji = false,
  enterKeySends = false,
  localBackupsEnabled = true,
  folderCount = 1,
  userUnregistered = false,
  clientDeprecated = false,
  isPlaintextExportEnabled = true,
  plaintextExportProgress = BackupCreationProgress.Idle,
  keepViewOnceMessages = false,
  ignoreRemoteDelete = false,
  ignoreAdminDelete = false,
  deleteMediaOnly = false,
  whoCanAddYouToGroups = "nonblocked"
)
