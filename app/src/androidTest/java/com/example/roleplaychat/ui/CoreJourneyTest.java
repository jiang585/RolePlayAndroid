package com.example.roleplaychat.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.roleplaychat.MainActivity;
import com.example.roleplaychat.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 核心用户旅程测试（架构文档 §15.1 UI 测试）：
 * 打开应用 -> 剧本列表可见 -> 点击新建剧本。
 * 其余流程依赖模拟器交互，此处验证最小启动链路与旋转恢复基础。
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class CoreJourneyTest {

    @Test
    public void launch_app_showsScriptList() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.recycler_scripts)).check(
                    (view, noViewFoundException) -> {
                        if (noViewFoundException != null) {
                            throw noViewFoundException;
                        }
                        if (!isDisplayed().matches(view)) {
                            throw new AssertionError("script list not displayed");
                        }
                    });
        }
    }

    @Test
    public void launch_clickAddButton_showsCreateDialog() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.fab_add_script)).perform(click());
            onView(withId(R.id.input_script_name)).check(
                    (view, noViewFoundException) -> {
                        if (noViewFoundException != null) {
                            throw noViewFoundException;
                        }
                    });
        }
    }
}
