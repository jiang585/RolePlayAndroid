package com.example.roleplaychat.ui.appearance;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.di.ViewModelFactory;
import com.example.roleplaychat.ui.common.FilePickerHelper;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * 装扮设置页（S9）：背景、气泡样式、颜色、暗化、字号。
 */
public class AppearanceFragment extends Fragment {

    private AppearanceViewModel viewModel;
    private ChipGroup bubbleStyleGroup;
    private MaterialButton pickImageButton;
    private MaterialButton resetBackgroundButton;
    private MaterialButton saveButton;
    private SeekBar dimSeekBar;
    private MaterialButton bubbleColorButton;
    private MaterialButton textColorButton;
    private MaterialButton nicknameColorButton;
    private ActivityResultLauncher<String> imagePicker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_appearance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(AppearanceViewModel.class);

        bindViews(view);

        String scriptId = getArguments() == null ? null : getArguments().getString("scriptId");
        String characterId = getArguments() == null ? null : getArguments().getString("characterId");
        if (characterId != null) {
            viewModel.loadCharacterAppearance(characterId);
        } else if (scriptId != null) {
            viewModel.loadScriptAppearance(scriptId);
        } else {
            viewModel.loadGlobalAppearance();
        }
        imagePicker = FilePickerHelper.registerImagePicker(this, viewModel::chooseBackgroundImage);
        pickImageButton.setOnClickListener(v -> imagePicker.launch("image/*"));
        resetBackgroundButton.setOnClickListener(v -> viewModel.resetBackground());

        bubbleStyleGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip chip = group.findViewById(checkedIds.get(0));
                if (chip != null) {
                    viewModel.setBubbleStyle((String) chip.getTag());
                }
            }
        });

        dimSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    viewModel.setBackgroundDim(progress / 100f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        bubbleColorButton.setOnClickListener(v -> showColorPicker(v, true));
        textColorButton.setOnClickListener(v -> showColorPicker(v, false));
        nicknameColorButton.setOnClickListener(v -> showColorPicker(v, false));

        saveButton.setOnClickListener(v -> viewModel.save());
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
    }

    private void bindViews(View view) {
        bubbleStyleGroup = view.findViewById(R.id.chip_group_bubble_style);
        pickImageButton = view.findViewById(R.id.btn_pick_background);
        resetBackgroundButton = view.findViewById(R.id.btn_reset_background);
        saveButton = view.findViewById(R.id.btn_save_appearance);
        dimSeekBar = view.findViewById(R.id.seek_background_dim);
        bubbleColorButton = view.findViewById(R.id.btn_bubble_color);
        textColorButton = view.findViewById(R.id.btn_text_color);
        nicknameColorButton = view.findViewById(R.id.btn_nickname_color);
    }

    private void showColorPicker(View anchor, boolean isBubble) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(isBubble ? R.string.appearance_bubble_color : R.string.appearance_text_color);
        String[] colors = {"#FF95EC69", "#FFFFFFFF", "#FFFFB3BA", "#FFB3D9FF", "#FFFFE0B3", "#FFE0B3FF"};
        builder.setItems(colors, (dialog, which) -> {
            String color = colors[which];
            if (isBubble) {
                viewModel.setBubbleColor(color);
            } else if (anchor == nicknameColorButton) {
                viewModel.setNicknameColor(color);
            } else {
                viewModel.setTextColor(color);
            }
        });
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.show();
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.equals("saved")) {
            Toast.makeText(requireContext(), R.string.appearance_saved, Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        } else if (value.equals("error:image")) {
            Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }
}
