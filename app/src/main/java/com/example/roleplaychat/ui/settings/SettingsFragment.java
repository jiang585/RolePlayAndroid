package com.example.roleplaychat.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.di.ViewModelFactory;
import com.example.roleplaychat.domain.model.ApiConfig;
import com.example.roleplaychat.ui.common.ErrorMessageMapper;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 设置页（S10）。
 */
public class SettingsFragment extends Fragment {

    private SettingsViewModel viewModel;
    private EditText baseUrlInput;
    private EditText apiKeyInput;
    private EditText modelInput;
    private EditText temperatureInput;
    private EditText topPInput;
    private EditText maxTokensInput;
    private Spinner providerSpinner;
    private boolean applyingConfig;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(SettingsViewModel.class);

        bindViews(view);
        viewModel.getConfig().observe(getViewLifecycleOwner(), this::populate);
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);

        MaterialButton saveButton = view.findViewById(R.id.btn_save_settings);
        saveButton.setOnClickListener(v -> save());

        MaterialButton testButton = view.findViewById(R.id.btn_test_connection);
        testButton.setOnClickListener(v -> {
            // 测试当前表单，而不是上一次已经保存的配置。
            save();
            viewModel.testConnection();
        });

        MaterialButton clearChatButton = view.findViewById(R.id.btn_clear_chat);
        clearChatButton.setOnClickListener(v -> showClearConfirm());
    }

    private void bindViews(View view) {
        providerSpinner = view.findViewById(R.id.input_provider);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.settings_provider_entries,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(adapter);
        baseUrlInput = view.findViewById(R.id.input_base_url);
        apiKeyInput = view.findViewById(R.id.input_api_key);
        modelInput = view.findViewById(R.id.input_model);
        temperatureInput = view.findViewById(R.id.input_temperature);
        topPInput = view.findViewById(R.id.input_top_p);
        maxTokensInput = view.findViewById(R.id.input_max_tokens);
        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (applyingConfig) {
                    return;
                }
                switch (providerForPosition(position)) {
                    case DEEPSEEK:
                        baseUrlInput.setText("https://api.deepseek.com");
                        modelInput.setText("deepseek-v4-flash");
                        break;
                    case OPENCODE_GO:
                        // https://opencode.ai/docs/go —— OpenAI 兼容端点，key 在 OpenCode Zen 控制台生成。
                        baseUrlInput.setText("https://opencode.ai/zen/go/v1");
                        modelInput.setText("deepseek-v4-flash");
                        break;
                    case OPENAI_COMPATIBLE:
                    default:
                        baseUrlInput.setText("https://api.openai.com/v1");
                        modelInput.setText("gpt-4o-mini");
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** Spinner 位置 -> Provider（必须与 settings_provider_entries 顺序一致）。 */
    private static ApiConfig.Provider providerForPosition(int position) {
        switch (position) {
            case 0:
                return ApiConfig.Provider.DEEPSEEK;
            case 2:
                return ApiConfig.Provider.OPENCODE_GO;
            case 1:
            default:
                return ApiConfig.Provider.OPENAI_COMPATIBLE;
        }
    }

    /** Provider -> Spinner 位置（必须与 settings_provider_entries 顺序一致）。 */
    private static int positionForProvider(ApiConfig.Provider provider) {
        switch (provider) {
            case DEEPSEEK:
                return 0;
            case OPENCODE_GO:
                return 2;
            case OPENAI_COMPATIBLE:
            default:
                return 1;
        }
    }

    private void populate(ApiConfig config) {
        if (config == null) {
            return;
        }
        applyingConfig = true;
        providerSpinner.setSelection(positionForProvider(config.getProvider()));
        baseUrlInput.setText(config.getBaseUrl());
        apiKeyInput.setText(config.getApiKey() == null ? "" : config.getApiKey());
        modelInput.setText(config.getModel());
        temperatureInput.setText(String.valueOf(config.getTemperature()));
        topPInput.setText(String.valueOf(config.getTopP()));
        maxTokensInput.setText(String.valueOf(config.getMaxTokens()));
        providerSpinner.post(() -> applyingConfig = false);
    }

    private void save() {
        float temperature = parseFloat(temperatureInput, 0.8f);
        float topP = parseFloat(topPInput, 0.9f);
        int maxTokens = parseInt(maxTokensInput, 2048);
        viewModel.save(
                providerForPosition(providerSpinner.getSelectedItemPosition()),
                textOf(baseUrlInput), textOf(apiKeyInput), textOf(modelInput),
                temperature, topP, maxTokens);
    }

    private void showClearConfirm() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_chat)
                .setMessage(R.string.settings_clear_all_confirm)
                .setPositiveButton(R.string.action_confirm, (dialog, which) ->
                        Toast.makeText(requireContext(), R.string.settings_clear_chat, Toast.LENGTH_SHORT).show())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.equals("saved")) {
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
        } else if (value.equals("test_ok")) {
            Toast.makeText(requireContext(), R.string.settings_test_success, Toast.LENGTH_SHORT).show();
        } else if (value.startsWith("test_fail:")) {
            String code = value.substring("test_fail:".length());
            int res = ErrorMessageMapper.map(
                    com.example.roleplaychat.domain.model.AppErrorCode.fromCode(code));
            Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show();
        } else if (value.startsWith("error:")) {
            Toast.makeText(requireContext(), value.substring("error:".length()),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private float parseFloat(EditText input, float fallback) {
        try {
            return Float.parseFloat(textOf(input));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int parseInt(EditText input, int fallback) {
        try {
            return Integer.parseInt(textOf(input));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
