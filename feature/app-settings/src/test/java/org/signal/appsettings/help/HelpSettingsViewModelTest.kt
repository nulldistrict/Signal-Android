/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelpSettingsViewModelTest {

  companion object {
    private const val VERSION_NAME = "8.33.0"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state contains the version name`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(HelpSettingsState(versionName = VERSION_NAME))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = assertEventEmits(HelpSettingsEvent.NavigateBackClicked, HelpSettingsAction.NavigateBack)

  @Test
  fun `SupportCenterClicked opens the support center`() = assertEventEmits(HelpSettingsEvent.SupportCenterClicked, HelpSettingsAction.OpenSupportCenter)

  @Test
  fun `ContactUsClicked navigates to contact us`() = assertEventEmits(HelpSettingsEvent.ContactUsClicked, HelpSettingsAction.NavigateToContactUs)

  @Test
  fun `VersionLongClicked copies the version name`() = assertEventEmits(HelpSettingsEvent.VersionLongClicked, HelpSettingsAction.CopyVersionToClipboard(VERSION_NAME))

  @Test
  fun `DebugLogClicked navigates to the debug log`() = assertEventEmits(HelpSettingsEvent.DebugLogClicked, HelpSettingsAction.NavigateToDebugLog)

  @Test
  fun `LicensesClicked navigates to licenses`() = assertEventEmits(HelpSettingsEvent.LicensesClicked, HelpSettingsAction.NavigateToLicenses)

  @Test
  fun `TermsAndPrivacyPolicyClicked opens the terms and privacy policy`() = assertEventEmits(HelpSettingsEvent.TermsAndPrivacyPolicyClicked, HelpSettingsAction.OpenTermsAndPrivacyPolicy)

  @Test
  fun `toggling logging on applies immediately`() = runTest(testDispatcher) {
    val changes = mutableListOf<Boolean>()
    val viewModel = createViewModel(initialLogEnabled = false, onLogEnabledChanged = { changes += it })

    viewModel.onEvent(HelpSettingsEvent.LoggingToggled(true))

    assertThat(changes).containsExactly(true)
    assertThat(viewModel.state.value.logEnabled).isEqualTo(true)
  }

  @Test
  fun `toggling logging off asks for confirmation first`() = runTest(testDispatcher) {
    val changes = mutableListOf<Boolean>()
    val viewModel = createViewModel(initialLogEnabled = true, onLogEnabledChanged = { changes += it })

    viewModel.onEvent(HelpSettingsEvent.LoggingToggled(false))

    assertThat(changes).isEmpty()
    assertThat(viewModel.state.value.logEnabled).isEqualTo(true)
    assertThat(viewModel.state.value.dialog).isEqualTo(HelpSettingsState.Dialog.ConfirmDisableLogging)
  }

  @Test
  fun `confirming disable logging applies the change and closes the dialog`() = runTest(testDispatcher) {
    val changes = mutableListOf<Boolean>()
    val viewModel = createViewModel(initialLogEnabled = true, onLogEnabledChanged = { changes += it })

    viewModel.onEvent(HelpSettingsEvent.LoggingToggled(false))
    viewModel.onEvent(HelpSettingsEvent.DisableLoggingConfirmed)

    assertThat(changes).containsExactly(false)
    assertThat(viewModel.state.value.logEnabled).isEqualTo(false)
    assertThat(viewModel.state.value.dialog).isEqualTo(HelpSettingsState.Dialog.None)
  }

  @Test
  fun `dismissing the dialog leaves logging enabled`() = runTest(testDispatcher) {
    val changes = mutableListOf<Boolean>()
    val viewModel = createViewModel(initialLogEnabled = true, onLogEnabledChanged = { changes += it })

    viewModel.onEvent(HelpSettingsEvent.LoggingToggled(false))
    viewModel.onEvent(HelpSettingsEvent.DialogDismissed)

    assertThat(changes).isEmpty()
    assertThat(viewModel.state.value.logEnabled).isEqualTo(true)
    assertThat(viewModel.state.value.dialog).isEqualTo(HelpSettingsState.Dialog.None)
  }

  private fun assertEventEmits(event: HelpSettingsEvent, expected: HelpSettingsAction) = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(event)

    assertThat(actions).containsExactly(expected)
  }

  private fun createViewModel(
    initialLogEnabled: Boolean = true,
    onLogEnabledChanged: (Boolean) -> Unit = {}
  ): HelpSettingsViewModel = HelpSettingsViewModel(
    versionName = VERSION_NAME,
    initialLogEnabled = initialLogEnabled,
    onLogEnabledChanged = onLogEnabledChanged
  )

  private fun TestScope.collectActions(actions: Flow<HelpSettingsAction>): List<HelpSettingsAction> {
    val collected = mutableListOf<HelpSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
