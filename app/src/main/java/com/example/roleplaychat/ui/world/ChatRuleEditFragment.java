package com.example.roleplaychat.ui.world;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.di.ViewModelFactory;
import com.example.roleplaychat.domain.model.WorldSetting;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 对话设定页：剧本级扮演要求 + 每轮最多回复角色数（入口在聊天页菜单）。
 * 扮演要求每次 AI 回复（含自动续演）都会注入系统提示词，等效长期生效。
 */
public class ChatRuleEditFragment extends Fragment {

    private ChatRuleEditViewModel viewModel;
    private TextInputEditText directiveInput;
    private SeekBar maxRespondersSeek;
    private android.widget.TextView maxRespondersValue;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_rule_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String scriptId = getArguments() == null ? null : getArguments().getString("scriptId");

        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(ChatRuleEditViewModel.class);

        directiveInput = view.findViewById(R.id.input_chat_style_directive);
        maxRespondersSeek = view.findViewById(R.id.seek_max_responders);
        maxRespondersValue = view.findViewById(R.id.tv_max_responders_value);
        MaterialButton saveButton = view.findViewById(R.id.btn_save_chat_rules);

        maxRespondersSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                maxRespondersValue.setText(String.valueOf(seekBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        viewModel.getWorld().observe(getViewLifecycleOwner(), this::populate);
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
        viewModel.start(scriptId);
        // world_settings 行可能不存在：等加载完成后再给默认值，避免覆盖。
        viewModel.getLoaded().observe(getViewLifecycleOwner(), loaded -> {
            if (Boolean.TRUE.equals(loaded) && viewModel.getWorld().getValue() == null) {
                maxRespondersSeek.setProgress(WorldSetting.DEFAULT_MAX_RESPONDERS);
                maxRespondersValue.setText(String.valueOf(WorldSetting.DEFAULT_MAX_RESPONDERS));
            }
        });

        saveButton.setOnClickListener(v -> {
            String directive = directiveInput.getText() == null
                    ? "" : directiveInput.getText().toString().trim();
            int max = Math.max(WorldSetting.MIN_MAX_RESPONDERS,
                    Math.min(maxRespondersSeek.getProgress(), WorldSetting.MAX_MAX_RESPONDERS));
            viewModel.save(directive, max, System.currentTimeMillis());
        });
    }

    private void populate(WorldSetting world) {
        if (world == null) {
            return;
        }
        directiveInput.setText(world.getChatStyleDirective() == null
                ? "" : world.getChatStyleDirective());
        maxRespondersSeek.setProgress(world.getMaxRespondersPerTurn());
        maxRespondersValue.setText(String.valueOf(world.getMaxRespondersPerTurn()));
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.equals("saved")) {
            Toast.makeText(requireContext(), R.string.chat_rules_saved, Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        } else if (value.startsWith("error:")) {
            Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }
}
