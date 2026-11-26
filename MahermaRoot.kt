package com.maherma.app.ui

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maherma.app.ui.conversation.ConversationScreen
import com.maherma.app.ui.home.HomeScreen
import com.maherma.app.ui.manage.ActivityScreen
import com.maherma.app.ui.manage.MemoryScreen
import com.maherma.app.ui.manage.NotesScreen
import com.maherma.app.ui.manage.TasksScreen
import com.maherma.app.ui.settings.SettingsScreen

@Composable
fun MahermaRoot(vm: AssistantViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(vm) { route -> nav.navigate(route) { launchSingleTop = true } } }
        composable("conversation") { ConversationScreen(vm, back) }
        composable("tasks") { TasksScreen(back) }
        composable("notes") { NotesScreen(back) }
        composable("memory") { MemoryScreen(back) }
        composable("activity") { ActivityScreen(back) }
        composable("settings") { SettingsScreen(back) }
    }
}
