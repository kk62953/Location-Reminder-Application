package com.udacity.project4.locationreminders.reminderslist

import android.app.Application
import android.os.Bundle
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.udacity.project4.R
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
//UI Testing
@MediumTest
class ReminderListFragmentTest : KoinTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var application: Application

    @Before
    fun initRepository() {
        stopKoin() // stop the original app koin
        application = getApplicationContext()
        val myModule = module {
            viewModel {
                RemindersListViewModel(
                    application,
                    get() as ReminderDataSource
                )
            }
            single {
                SaveReminderViewModel(
                    application,
                    get() as ReminderDataSource
                )
            }
            single { RemindersLocalRepository(get()) as ReminderDataSource }
            single { LocalDB.createRemindersDao(application) }
        }
        // declare a new koin module
        startKoin {
            modules(listOf(myModule))
        }
        val repository: ReminderDataSource by inject()


        // clear the data to start fresh
        runBlocking {
            repository.deleteAllReminders()
        }
    }

    @After
    fun stopKoinAfterTest() = stopKoin()

    @Test
    fun addNewReminder_navigatesToSaveReminder() {
        // GIVEN - on ReminderList
        val reminderListFragment = launchFragmentInContainer<ReminderListFragment>(
            Bundle(),
            R.style.AppTheme
        )
        val navController = mock(NavController::class.java)
        reminderListFragment.onFragment {
            Navigation.setViewNavController(it.view!!, navController)
        }

        // WHEN - clicked on the Floating Button to add a reminder
        onView(withId(R.id.addReminderFAB)).perform(click())

        // THEN validate that the screen has navigate to the Save Reminder Fragment
        verify(navController).navigate(ReminderListFragmentDirections.toSaveReminder())
    }


}