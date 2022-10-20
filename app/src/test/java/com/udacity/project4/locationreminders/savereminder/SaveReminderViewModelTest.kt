import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.udacity.project4.locationreminders.MainCoroutineRule
import com.udacity.project4.locationreminders.data.FakeDataSource
import com.udacity.project4.locationreminders.getOrAwaitValue
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.core.Is.`is`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SaveReminderViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var application: Application
    private lateinit var dataSource: FakeDataSource
    private lateinit var saveReminderViewModel: SaveReminderViewModel

    @Before
    fun setupViewModel() {
        stopKoin()
        application = ApplicationProvider.getApplicationContext()
        FirebaseApp.initializeApp(application)
        dataSource = FakeDataSource()
        saveReminderViewModel = SaveReminderViewModel(application, dataSource)
    }

    @Test
    fun validate_loadingOfReminders() = mainCoroutineRule.runBlockingTest {
        //GIVEN
        val reminder = ReminderDataItem(
            "Title", "Description",
            "Location", 21.8359, 88.8842
        )
        //WHEN
        mainCoroutineRule.pauseDispatcher()
        saveReminderViewModel.validateAndSaveReminder(reminder)
        //THAT
        assertThat(saveReminderViewModel.showLoading.getOrAwaitValue(), `is`(true))
        //WHEN
        mainCoroutineRule.resumeDispatcher()
        //THAT
        assertThat(saveReminderViewModel.showLoading.getOrAwaitValue(), `is`(false))
    }

    @Test
    fun validate_savingRemindersLocationEmpty() = mainCoroutineRule.runBlockingTest {
        //GIVEN
        val reminder = ReminderDataItem(
            "Title", "Reminder Location",
            "", 21.8359, 88.8842
        )

        //WHEN
        mainCoroutineRule.pauseDispatcher()
        saveReminderViewModel.validateAndSaveReminder(reminder)
        //THAT
        assertThat(
            saveReminderViewModel.showSnackBarInt.getOrAwaitValue(),
            `is`(Matchers.notNullValue())
        )

    }

    @Test
    fun validate_savingRemindersTitleNull() = mainCoroutineRule.runBlockingTest {
        //GIVEN
        val reminder = ReminderDataItem(
            null, "Reminder Location",
            "Location", 21.8359, 88.8842
        )

        //WHEN
        mainCoroutineRule.pauseDispatcher()
        saveReminderViewModel.validateAndSaveReminder(reminder)
        //THAT
        assertThat(
            saveReminderViewModel.showSnackBarInt.getOrAwaitValue(),
            `is`(Matchers.notNullValue())
        )

    }

}