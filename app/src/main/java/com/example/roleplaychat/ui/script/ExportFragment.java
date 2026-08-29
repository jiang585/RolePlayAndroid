package com.example.roleplaychat.ui.script;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.data.file.LocalAssetStore;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.CharacterProfile;
import com.example.roleplaychat.domain.usecase.ExportDataUseCase;
import com.example.roleplaychat.domain.usecase.ImportDataUseCase;
import com.example.roleplaychat.ui.common.FilePickerHelper;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * 导入导出页（FR-701~708）：分项导出/导入角色卡、世界观、聊天记录、剧本包。
 * 文件与 DB 操作在后台线程（§3.2），完成后通过主线程回传 Toast/分享。
 */
public class ExportFragment extends Fragment {

    private String scriptId;
    private ActivityResultLauncher<String[]> importPicker;
    private RolePlayChatApp app;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            scriptId = getArguments().getString("scriptId");
        }
        app = (RolePlayChatApp) requireActivity().getApplication();

        importPicker = FilePickerHelper.registerFilePicker(this, this::onImportFilePicked);

        MaterialButton exportCharacter = view.findViewById(R.id.btn_export_character);
        exportCharacter.setOnClickListener(v -> pickCharacterToExport());

        MaterialButton exportWorld = view.findViewById(R.id.btn_export_world);
        exportWorld.setOnClickListener(v -> runExport(ExportDataUseCase.ExportType.WORLD, null));

        MaterialButton exportChat = view.findViewById(R.id.btn_export_chat);
        exportChat.setOnClickListener(v -> chooseChatFormat());

        MaterialButton exportPackage = view.findViewById(R.id.btn_export_package);
        exportPackage.setOnClickListener(v -> runExport(ExportDataUseCase.ExportType.SCRIPT_PACKAGE, null));

        MaterialButton importCharacter = view.findViewById(R.id.btn_import_character);
        importCharacter.setOnClickListener(v ->
                importPicker.launch(new String[]{"application/json", "text/json", "text/plain"}));
    }

    private void runExport(ExportDataUseCase.ExportType type, String characterId) {
        app.container().executors.diskIO().execute(() -> {
            LocalAssetStore assetStore = app.container().assetStore;
            String extension = type == ExportDataUseCase.ExportType.CHAT_TXT ? ".txt"
                    : type == ExportDataUseCase.ExportType.CHAT_PDF ? ".pdf" : ".json";
            String mime = type == ExportDataUseCase.ExportType.CHAT_TXT ? "text/plain"
                    : type == ExportDataUseCase.ExportType.CHAT_PDF ? "application/pdf" : "application/json";
            File target = new File(assetStore.exportsDir(), "export_" + type.name().toLowerCase(Locale.ROOT)
                    + "_" + System.currentTimeMillis() + extension);
            AppError error = app.container().exportDataUseCase.execute(type, scriptId, characterId, target, false);
            if (error != null) {
                final String message = error.getMessage();
                app.container().executors.mainThread().execute(() ->
                        Toast.makeText(requireContext(),
                                getString(R.string.import_export_export_failed, message),
                                Toast.LENGTH_SHORT).show());
                return;
            }
            final Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", target);
            app.container().executors.mainThread().execute(() ->
                    FilePickerHelper.shareFile(requireActivity(), uri, mime));
        });
    }

    private void chooseChatFormat() {
        String[] formats = {getString(R.string.chat_export_json), getString(R.string.chat_export_txt),
                getString(R.string.chat_export_pdf)};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.import_export_chat)
                .setItems(formats, (dialog, which) -> runExport(which == 0
                        ? ExportDataUseCase.ExportType.CHAT_JSON
                        : which == 1 ? ExportDataUseCase.ExportType.CHAT_TXT
                        : ExportDataUseCase.ExportType.CHAT_PDF, null))
                .show();
    }

    private void pickCharacterToExport() {
        app.container().executors.diskIO().execute(() -> {
            List<CharacterProfile> characters = app.container().characterRepository.getEnabledByScriptId(scriptId);
            if (characters.isEmpty()) {
                app.container().executors.mainThread().execute(() ->
                        Toast.makeText(requireContext(), R.string.character_list_empty, Toast.LENGTH_SHORT).show());
                return;
            }
            final String[] names = new String[characters.size()];
            for (int i = 0; i < characters.size(); i++) {
                names[i] = characters.get(i).getName();
            }
            final List<CharacterProfile> finalCharacters = characters;
            app.container().executors.mainThread().execute(() ->
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.import_export_character)
                            .setItems(names, (dialog, which) ->
                                    runExport(ExportDataUseCase.ExportType.CHARACTER,
                                            finalCharacters.get(which).getId()))
                            .show());
        });
    }

    private void onImportFilePicked(Uri uri) {
        app.container().executors.diskIO().execute(() -> {
            try {
                File temp = new File(app.container().assetStore.tmpDir(),
                        "import_" + System.currentTimeMillis() + ".json");
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(temp)) {
                    if (in == null) {
                        throw new java.io.IOException("cannot open");
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                AppError[] error = new AppError[1];
                String id = app.container().importDataUseCase.execute(
                        ImportDataUseCase.ImportType.CHARACTER,
                        temp, scriptId, ImportDataUseCase.ImportMode.CREATE_NEW, error);
                if (id != null) {
                    app.container().executors.mainThread().execute(() ->
                            Toast.makeText(requireContext(), R.string.import_export_import_done,
                                    Toast.LENGTH_SHORT).show());
                } else {
                    final String message = error[0] == null ? "unknown" : error[0].getMessage();
                    app.container().executors.mainThread().execute(() ->
                            Toast.makeText(requireContext(),
                                    getString(R.string.import_export_import_failed, message),
                                    Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                app.container().executors.mainThread().execute(() ->
                        Toast.makeText(requireContext(), R.string.import_export_invalid_file,
                                Toast.LENGTH_SHORT).show());
            }
        });
    }
}
