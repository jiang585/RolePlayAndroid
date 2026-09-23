package com.example.roleplaychat.ui.character;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.di.ViewModelFactory;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.model.CharacterVisualProfile;
import com.example.roleplaychat.ui.common.FilePickerHelper;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.stream.Collectors;

/**
 * 角色编辑页（S5 编辑部分）：头像、姓名、设定字段、AI 完善入口。
 */
public class CharacterEditFragment extends Fragment {

    private String scriptId;
    private String characterId;
    private CharacterEditViewModel viewModel;

    private ImageView avatarView;
    private EditText nameInput;
    private EditText aliasesInput;
    private EditText genderInput;
    private EditText ageInput;
    private EditText personalityInput;
    private EditText backstoryInput;
    private EditText speakingStyleInput;
    private EditText catchphrasesInput;
    private EditText strengthsInput;
    private EditText flawsInput;
    private EditText relationshipsInput;
    private EditText sampleLinesInput;
    private EditText systemPromptInput;
    private EditText hiddenSettingInput;
    private MaterialButton saveButton;
    private MaterialButton aiEnhanceButton;
    private View aiProgressBar;
    private android.widget.TextView aiProgressText;

    private ActivityResultLauncher<String> imagePicker;
    private ActivityResultLauncher<String> facePicker;
    private ImageView visualFaceView;
    private EditText visualDescriptionInput;
    private EditText visualHeightInput;
    private EditText visualBodyInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_character_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            scriptId = getArguments().getString("scriptId");
            characterId = getArguments().getString("characterId");
        }
        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(CharacterEditViewModel.class);

        bindViews(view);
        imagePicker = FilePickerHelper.registerImagePicker(this, viewModel::importAvatar);
        facePicker = FilePickerHelper.registerImagePicker(this, uri -> {
            viewModel.setFaceUri(uri);
            if (uri != null) Glide.with(this).load(uri).into(visualFaceView);
        });
        avatarView.setOnClickListener(v -> imagePicker.launch("image/*"));
        view.findViewById(R.id.btn_pick_visual_face).setOnClickListener(v -> facePicker.launch("image/*"));
        view.findViewById(R.id.btn_generate_visual_face).setOnClickListener(v -> {
            viewModel.setVisualFields(textOf(visualDescriptionInput), textOf(visualHeightInput), textOf(visualBodyInput));
            viewModel.generateVisualCandidates(textOf(visualDescriptionInput));
        });
        saveButton.setOnClickListener(v -> save());
        aiEnhanceButton.setOnClickListener(v -> showAiEnhanceDialog());

        viewModel.getAvatarRef().observe(getViewLifecycleOwner(), this::renderAvatar);
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
        viewModel.getLoaded().observe(getViewLifecycleOwner(), loaded -> {
            if (Boolean.TRUE.equals(loaded)) {
                populate(viewModel.getEditing());
            }
        });
        viewModel.getAiGenerating().observe(getViewLifecycleOwner(), value -> renderGenerating(Boolean.TRUE.equals(value)));
        viewModel.getAiProgress().observe(getViewLifecycleOwner(), aiProgressText::setText);
        viewModel.getAiDraft().observe(getViewLifecycleOwner(), this::populate);
        viewModel.getVisualProfile().observe(getViewLifecycleOwner(), this::renderVisualProfile);
        viewModel.getVisualGenerating().observe(getViewLifecycleOwner(), generating ->
                view.findViewById(R.id.btn_generate_visual_face).setEnabled(!Boolean.TRUE.equals(generating)));
        // 后台加载角色（DB 操作不得在主线程，架构文档 §3.2）
        ((RolePlayChatApp) requireActivity().getApplication()).container().executors
                .diskIO().execute(() -> viewModel.load(scriptId, characterId));
    }

    private void bindViews(View view) {
        avatarView = view.findViewById(R.id.iv_edit_avatar);
        nameInput = view.findViewById(R.id.input_char_name);
        aliasesInput = view.findViewById(R.id.input_char_aliases);
        genderInput = view.findViewById(R.id.input_char_gender);
        ageInput = view.findViewById(R.id.input_char_age);
        personalityInput = view.findViewById(R.id.input_char_personality);
        backstoryInput = view.findViewById(R.id.input_char_backstory);
        speakingStyleInput = view.findViewById(R.id.input_char_speaking_style);
        catchphrasesInput = view.findViewById(R.id.input_char_catchphrases);
        strengthsInput = view.findViewById(R.id.input_char_strengths);
        flawsInput = view.findViewById(R.id.input_char_flaws);
        relationshipsInput = view.findViewById(R.id.input_char_relationships);
        sampleLinesInput = view.findViewById(R.id.input_char_sample_lines);
        systemPromptInput = view.findViewById(R.id.input_char_system_prompt);
        hiddenSettingInput = view.findViewById(R.id.input_char_hidden_setting);
        visualFaceView = view.findViewById(R.id.iv_visual_face);
        visualDescriptionInput = view.findViewById(R.id.input_visual_description);
        visualHeightInput = view.findViewById(R.id.input_visual_height);
        visualBodyInput = view.findViewById(R.id.input_visual_body);
        saveButton = view.findViewById(R.id.btn_save_character);
        aiEnhanceButton = view.findViewById(R.id.btn_ai_enhance_character);
        aiProgressBar = view.findViewById(R.id.progress_character_ai);
        aiProgressText = view.findViewById(R.id.text_character_ai_progress);
    }

    private void populate(@Nullable CharacterProfile profile) {
        if (profile == null) {
            return;
        }
        nameInput.setText(profile.getName());
        aliasesInput.setText(joinLines(profile.getAliases()));
        genderInput.setText(profile.getGender() == null ? "" : profile.getGender());
        ageInput.setText(profile.getAgeText() == null ? "" : profile.getAgeText());
        personalityInput.setText(profile.getPersonality() == null ? "" : profile.getPersonality());
        backstoryInput.setText(profile.getBackstory() == null ? "" : profile.getBackstory());
        speakingStyleInput.setText(profile.getSpeakingStyle() == null ? "" : profile.getSpeakingStyle());
        catchphrasesInput.setText(joinLines(profile.getCatchphrases()));
        strengthsInput.setText(joinLines(profile.getStrengths()));
        flawsInput.setText(joinLines(profile.getFlaws()));
        relationshipsInput.setText(profile.getRelationships().entrySet().stream()
                .map(e -> e.getKey() + "：" + e.getValue())
                .collect(Collectors.joining("\n")));
        sampleLinesInput.setText(joinLines(profile.getSampleLines()));
        systemPromptInput.setText(profile.getSystemPrompt() == null ? "" : profile.getSystemPrompt());
        hiddenSettingInput.setText(profile.getHiddenSetting() == null ? "" : profile.getHiddenSetting());
    }

    private void renderAvatar(@Nullable String avatarRef) {
        if (avatarRef == null || avatarRef.isEmpty()) {
            avatarView.setImageResource(R.drawable.ic_avatar_placeholder);
            return;
        }
        java.io.File file = ((RolePlayChatApp) requireActivity().getApplication())
                .container().assetStore.resolve(avatarRef);
        if (file != null) {
            Glide.with(this).load(file).circleCrop().into(avatarView);
        } else {
            avatarView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    private void save() {
        viewModel.setVisualFields(textOf(visualDescriptionInput), textOf(visualHeightInput), textOf(visualBodyInput));
        viewModel.save(
                textOf(nameInput), textOf(aliasesInput), textOf(genderInput), textOf(ageInput),
                textOf(personalityInput), textOf(backstoryInput), textOf(speakingStyleInput),
                textOf(catchphrasesInput), textOf(strengthsInput), textOf(flawsInput),
                textOf(relationshipsInput), textOf(sampleLinesInput), textOf(systemPromptInput),
                textOf(hiddenSettingInput), System.currentTimeMillis());
    }

    private void renderVisualProfile(@Nullable CharacterVisualProfile profile) {
        if (profile == null) return;
        visualFaceView.setOnClickListener(v -> {
            if (profile.getAssets().size() <= 1) return;
            String[] labels = new String[profile.getAssets().size()];
            int checked = 0;
            for (int i = 0; i < profile.getAssets().size(); i++) {
                labels[i] = "候选 " + (i + 1);
                if (profile.getAssets().get(i).isPrimary()) checked = i;
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("选择主参考脸")
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        viewModel.selectVisualCandidate(which); dialog.dismiss();
                    }).show();
        });
        visualDescriptionInput.setText(profile.getIdentityPrompt());
        String appearance = profile.getAppearanceJson();
        if (appearance != null) {
            for (String part : appearance.split("；")) {
                if (part.startsWith("身高：")) visualHeightInput.setText(part.substring(3));
                if (part.startsWith("体型：")) visualBodyInput.setText(part.substring(3));
            }
        }
        if (!profile.getAssets().isEmpty()) {
            java.io.File file = ((RolePlayChatApp) requireActivity().getApplication())
                    .container().assetStore.resolve(profile.getAssets().get(0).getLocalPath());
            if (file != null) Glide.with(this).load(file).into(visualFaceView);
        }
    }

    private void showAiEnhanceDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.character_ai_hint);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.character_ai_enhance)
                .setView(input)
                .setPositiveButton(R.string.action_confirm, (dialog, which) ->
                        viewModel.aiEnhance(textOf(input)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void renderGenerating(boolean generating) {
        aiEnhanceButton.setEnabled(!generating);
        saveButton.setEnabled(!generating);
        aiProgressBar.setVisibility(generating ? View.VISIBLE : View.GONE);
        aiProgressText.setVisibility(generating ? View.VISIBLE : View.GONE);
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.equals("saved")) {
            Toast.makeText(requireContext(), R.string.character_saved, Toast.LENGTH_SHORT).show();
            if (characterId == null && viewModel.getEditing() != null) {
                characterId = viewModel.getEditing().getId();
            }
            requireActivity().getSupportFragmentManager().popBackStack();
        } else if (value.startsWith("error:")) {
            String code = value.substring("error:".length());
            if (code.equals("avatar")) {
                Toast.makeText(requireContext(), "头像导入失败", Toast.LENGTH_SHORT).show();
            } else if (code.equals("face") || code.equals("face_required")) {
                Toast.makeText(requireContext(), "请上传清晰正脸参考图", Toast.LENGTH_LONG).show();
            } else if (code.equals("measurements_required")) {
                Toast.makeText(requireContext(), "有图剧本需要填写身高和体型", Toast.LENGTH_LONG).show();
            } else if (code.equals("save_before_visual")) {
                Toast.makeText(requireContext(), "请先保存角色，再生成视觉身份候选", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(requireContext(), com.example.roleplaychat.ui.common.ErrorMessageMapper.map(
                        com.example.roleplaychat.domain.model.AppErrorCode.fromCode(code)), Toast.LENGTH_SHORT).show();
            }
        } else if (value.equals("ai_done")) {
            Toast.makeText(requireContext(), R.string.character_ai_done, Toast.LENGTH_SHORT).show();
        } else if (value.equals("disabled")) {
            Toast.makeText(requireContext(), R.string.character_disable, Toast.LENGTH_SHORT).show();
        }
    }

    private String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private String joinLines(java.util.List<String> lines) {
        return lines == null ? "" : String.join("\n", lines);
    }
}
