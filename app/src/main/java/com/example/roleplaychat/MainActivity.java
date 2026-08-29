package com.example.roleplaychat;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.roleplaychat.databinding.ActivityMainBinding;

/**
 * 单 Activity 导航宿主（架构文档 §10.1）。
 * 使用 NoActionBar 主题 + 显式 Toolbar 作为 ActionBar。
 */
public class MainActivity extends AppCompatActivity {

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph())
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (destination.getId() == R.id.scriptListFragment) {
                    setupScriptListToolbar();
                } else {
                    binding.toolbar.getMenu().clear();
                    binding.toolbar.setOnMenuItemClickListener(null);
                    // Settings is entered directly from the script list toolbar. Keep an
                    // explicit up action here so dynamic toolbar updates cannot leave the
                    // navigation arrow without a click handler.
                    if (destination.getId() == R.id.settingsFragment) {
                        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
                        binding.toolbar.setNavigationContentDescription(R.string.action_back);
                        binding.toolbar.setNavigationOnClickListener(v -> navController.navigateUp());
                    }
                }
            });
            setupScriptListToolbar();
        }
    }

    public void refreshScriptListToolbar() {
        findViewById(R.id.toolbar).post(this::setupScriptListToolbar);
    }

    private void setupScriptListToolbar() {
        if (navController == null) {
            return;
        }
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // The list owns its editorial heading; keep the top bar quiet like the reference layout.
        toolbar.setTitle(null);
        toolbar.setNavigationIcon(null);
        toolbar.getMenu().clear();
        MenuItem settings = toolbar.getMenu().add(R.string.action_settings);
        settings.setIcon(R.drawable.ic_settings);
        settings.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        settings.setContentDescription(getString(R.string.action_settings));
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == settings.getItemId()) {
                navController.navigate(R.id.action_scriptList_to_settings);
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController != null && navController.navigateUp()
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (navController != null && navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.scriptListFragment) {
            refreshScriptListToolbar();
        }
    }
}
