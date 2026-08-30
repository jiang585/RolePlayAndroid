package com.example.roleplaychat.ui.script;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.di.ViewModelFactory;
import com.example.roleplaychat.domain.model.Script;
import com.example.roleplaychat.ui.common.FilePickerHelper;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 剧本编辑页（S2）：新建或编辑元信息，支持封面上传 / 更换 / 清除。
 */
public class ScriptEditFragment extends Fragment {

    private ScriptEditViewModel viewModel;
    private TextInputEditText nameInput;
    private TextInputEditText lineInput;
    private ImageView coverPreview;
    private ImageView clearCoverButton;
    private ActivityResultLauncher<String> imagePicker;
    private String scriptId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_script_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(ScriptEditViewModel.class);

        if (getArguments() != null) {
            scriptId = getArguments().getString("scriptId");
        }

        nameInput = view.findViewById(R.id.input_script_name);
        lineInput = view.findViewById(R.id.input_script_one_line);
        coverPreview = view.findViewById(R.id.cover_preview);
        clearCoverButton = view.findViewById(R.id.btn_clear_cover);
        MaterialButton saveButton = view.findViewById(R.id.btn_save_script);

        // 后台加载已有剧本（DB 操作不得在主线程）
        viewModel.load(scriptId);
        if (scriptId != null) {
            viewModel.getScript().observe(getViewLifecycleOwner(), this::populate);
        }

        imagePicker = FilePickerHelper.registerImagePicker(this, viewModel::importCover);
        coverPreview.setOnClickListener(v -> imagePicker.launch("image/*"));
        clearCoverButton.setOnClickListener(v -> viewModel.clearCover());

        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText() == null ? "" : nameInput.getText().toString();
            String line = lineInput.getText() == null ? "" : lineInput.getText().toString();
            long now = System.currentTimeMillis();
            if (scriptId == null) {
                viewModel.create(name, line, now);
            } else {
                Script current = viewModel.getScript() == null ? null : viewModel.getScript().getValue();
                if (current != null) {
                    viewModel.update(current, name, line, now);
                } else {
                    viewModel.create(name, line, now);
                }
            }
        });

        viewModel.getCoverRef().observe(getViewLifecycleOwner(), this::renderCover);
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
    }

    private void populate(Script script) {
        if (script == null) {
            return;
        }
        nameInput.setText(script.getName());
        lineInput.setText(script.getOneLine() == null ? "" : script.getOneLine());
    }

    private void renderCover(@Nullable String coverRef) {
        boolean hasCover = coverRef != null && !coverRef.isEmpty();
        clearCoverButton.setVisibility(hasCover ? View.VISIBLE : View.GONE);
        if (!hasCover) {
            coverPreview.setImageResource(R.drawable.ic_image_placeholder);
            return;
        }
        java.io.File file = ((RolePlayChatApp) requireActivity().getApplication())
                .container().assetStore.resolve(coverRef);
        if (file != null) {
            Glide.with(this).load(file).centerCrop().into(coverPreview);
        } else {
            coverPreview.setImageResource(R.drawable.ic_image_placeholder);
        }
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.startsWith("created:")) {
            String id = value.substring("created:".length());
            Toast.makeText(requireContext(), R.string.script_created, Toast.LENGTH_SHORT).show();
            NavController navController = Navigation.findNavController(requireView());
            Bundle args = new Bundle();
            args.putString("scriptId", id);
            navController.navigate(R.id.action_scriptEdit_to_scriptDetail, args);
        } else if (value.equals("saved")) {
            Toast.makeText(requireContext(), R.string.script_saved, Toast.LENGTH_SHORT).show();
            Navigation.findNavController(requireView()).navigateUp();
        } else if (value.startsWith("error:")) {
            String code = value.substring("error:".length());
            if (code.equals("cover")) {
                Toast.makeText(requireContext(), R.string.script_cover_import_failed,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), code, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
