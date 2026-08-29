package com.example.roleplaychat.ui.world;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.stream.Collectors;

/**
 * 世界观编辑页（S4）。
 */
public class WorldEditFragment extends Fragment {

    private String scriptId;
    private WorldEditViewModel viewModel;

    private EditText eraInput;
    private EditText locationInput;
    private EditText factionsInput;
    private EditText rulesInput;
    private EditText storyHookInput;
    private EditText backgroundInput;
    private EditText tagsInput;
    private EditText versionNoteInput;
    private MaterialButton saveButton;
    private MaterialButton aiEnhanceButton;
    private View aiProgressBar;
    private android.widget.TextView aiProgressText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_world_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            scriptId = getArguments().getString("scriptId");
        }
        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(WorldEditViewModel.class);
        viewModel.setScriptId(scriptId);

        bindViews(view);
        saveButton.setOnClickListener(v -> save());
        aiEnhanceButton.setOnClickListener(v -> showAiEnhanceDialog());

        viewModel.getWorld().observe(getViewLifecycleOwner(), this::populate);
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
        viewModel.getAiGenerating().observe(getViewLifecycleOwner(), generating ->
                renderGenerating(Boolean.TRUE.equals(generating)));
        viewModel.getAiProgress().observe(getViewLifecycleOwner(), text -> aiProgressText.setText(text));
        viewModel.getAiDraft().observe(getViewLifecycleOwner(), this::populate);
    }

    private void bindViews(View view) {
        eraInput = view.findViewById(R.id.input_world_era);
        locationInput = view.findViewById(R.id.input_world_location);
        factionsInput = view.findViewById(R.id.input_world_factions);
        rulesInput = view.findViewById(R.id.input_world_rules);
        storyHookInput = view.findViewById(R.id.input_world_story_hook);
        backgroundInput = view.findViewById(R.id.input_world_background);
        tagsInput = view.findViewById(R.id.input_world_tags);
        versionNoteInput = view.findViewById(R.id.input_world_version_note);
        saveButton = view.findViewById(R.id.btn_save_world);
        aiEnhanceButton = view.findViewById(R.id.btn_ai_enhance_world);
        aiProgressBar = view.findViewById(R.id.progress_world_ai);
        aiProgressText = view.findViewById(R.id.text_world_ai_progress);
    }

    private void populate(WorldSetting world) {
        if (world == null) {
            return;
        }
        eraInput.setText(world.getEra() == null ? "" : world.getEra());
        locationInput.setText(world.getLocation() == null ? "" : world.getLocation());
        factionsInput.setText(String.join("\n", world.getFactions()));
        rulesInput.setText(String.join("\n", world.getRules()));
        storyHookInput.setText(world.getStoryHook() == null ? "" : world.getStoryHook());
        backgroundInput.setText(world.getBackgroundFull() == null ? "" : world.getBackgroundFull());
        tagsInput.setText(String.join("\n", world.getTags()));
        versionNoteInput.setText(world.getVersionNote() == null ? "" : world.getVersionNote());
    }

    private void save() {
        viewModel.save(
                textOf(eraInput), textOf(locationInput), textOf(factionsInput), textOf(rulesInput),
                textOf(storyHookInput), textOf(backgroundInput), textOf(tagsInput),
                textOf(versionNoteInput), System.currentTimeMillis());
    }

    private void showAiEnhanceDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.world_ai_hint);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.world_ai_enhance)
                .setView(input)
                .setPositiveButton(R.string.action_confirm, (dialog, which) ->
                        viewModel.aiEnhance(input.getText() == null ? "" : input.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.equals("saved")) {
            Toast.makeText(requireContext(), R.string.world_saved, Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        } else if (value.equals("ai_done")) {
            Toast.makeText(requireContext(), R.string.world_ai_done, Toast.LENGTH_SHORT).show();
        } else if (value.startsWith("error:")) {
            String code = value.substring("error:".length());
            Toast.makeText(requireContext(), com.example.roleplaychat.ui.common.ErrorMessageMapper.map(
                    com.example.roleplaychat.domain.model.AppErrorCode.fromCode(code)), Toast.LENGTH_SHORT).show();
        }
    }

    private void renderGenerating(boolean generating) {
        aiEnhanceButton.setEnabled(!generating);
        saveButton.setEnabled(!generating);
        aiProgressBar.setVisibility(generating ? View.VISIBLE : View.GONE);
        aiProgressText.setVisibility(generating ? View.VISIBLE : View.GONE);
    }

    private String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
