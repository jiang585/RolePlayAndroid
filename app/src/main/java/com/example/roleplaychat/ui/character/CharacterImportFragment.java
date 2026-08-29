package com.example.roleplaychat.ui.character;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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

/**
 * 角色卡导入页（S6）：文件选择 -> 预览确认。
 */
public class CharacterImportFragment extends Fragment {

    private String scriptId;
    private CharacterImportViewModel viewModel;
    private TextView previewText;
    private MaterialButton importButton;
    private ActivityResultLauncher<String[]> filePicker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_character_import, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            scriptId = getArguments().getString("scriptId");
        }
        ViewModelFactory factory = new ViewModelFactory(
                ((RolePlayChatApp) requireActivity().getApplication()).container());
        viewModel = new ViewModelProvider(this, factory).get(CharacterImportViewModel.class);

        previewText = view.findViewById(R.id.tv_import_preview);
        MaterialButton pickButton = view.findViewById(R.id.btn_pick_card_file);
        importButton = view.findViewById(R.id.btn_confirm_import);
        importButton.setEnabled(false);

        filePicker = FilePickerHelper.registerFilePicker(this, this::onFilePicked);
        pickButton.setOnClickListener(v ->
                filePicker.launch(new String[]{"application/json", "text/json", "text/plain"}));

        importButton.setOnClickListener(v -> viewModel.confirm(scriptId));

        viewModel.getPreview().observe(getViewLifecycleOwner(), preview -> {
            previewText.setText(preview);
            importButton.setEnabled(preview != null && !preview.isEmpty());
        });
        viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
    }

    private void onFilePicked(Uri uri) {
        viewModel.prepare(uri, requireContext());
    }

    private void handleEvent(SingleEvent<String> event) {
        String value = event == null ? null : event.getContentIfNotHandled();
        if (value == null) {
            return;
        }
        if (value.startsWith("imported:")) {
            Toast.makeText(requireContext(), R.string.character_import_done, Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
        } else if (value.startsWith("error:")) {
            Toast.makeText(requireContext(), R.string.character_import_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
