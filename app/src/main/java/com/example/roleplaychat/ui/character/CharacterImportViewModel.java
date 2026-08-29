package com.example.roleplaychat.ui.character;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.usecase.ImportDataUseCase;
import com.example.roleplaychat.ui.common.SingleEvent;
import com.example.roleplaychat.util.AppExecutors;

import java.io.File;

/**
 * 角色卡导入 ViewModel（架构文档 §9.6：读取 -> 预览 -> 确认 -> 写入）。
 * 文件与 DB 操作在后台线程（§3.2）。
 */
public class CharacterImportViewModel extends ViewModel {

    private final ImportDataUseCase importDataUseCase;
    private final AppExecutors executors;
    private final MutableLiveData<SingleEvent<String>> events = new MutableLiveData<>();
    private final MutableLiveData<String> preview = new MutableLiveData<>();
    private File pendingFile;

    public CharacterImportViewModel(ImportDataUseCase importDataUseCase, AppExecutors executors) {
        this.importDataUseCase = importDataUseCase;
        this.executors = executors;
    }

    public MutableLiveData<SingleEvent<String>> getEvents() {
        return events;
    }

    public MutableLiveData<String> getPreview() {
        return preview;
    }

    /** 从 URI 复制到临时文件并预览。 */
    public void prepare(android.net.Uri uri, android.content.Context context) {
        executors.diskIO().execute(() -> {
            try {
                java.io.InputStream in = context.getContentResolver().openInputStream(uri);
                if (in == null) {
                    events.postValue(new SingleEvent<>("error:open"));
                    return;
                }
                File dir = new File(context.getCacheDir(), "imports");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File temp = new File(dir, "card_" + System.nanoTime() + ".json");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(temp)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                in.close();
                pendingFile = temp;
                String text = importDataUseCase.preview(temp);
                if (text == null || text.isEmpty()) {
                    events.postValue(new SingleEvent<>("error:format"));
                    pendingFile = null;
                } else {
                    preview.postValue(text);
                }
            } catch (Exception e) {
                events.postValue(new SingleEvent<>("error:" + e.getMessage()));
            }
        });
    }

    /** 确认导入。 */
    public void confirm(String scriptId) {
        if (pendingFile == null) {
            events.postValue(new SingleEvent<>("error:no_file"));
            return;
        }
        executors.diskIO().execute(() -> {
            AppError[] error = new AppError[1];
            String id = importDataUseCase.execute(
                    ImportDataUseCase.ImportType.CHARACTER,
                    pendingFile,
                    scriptId,
                    ImportDataUseCase.ImportMode.CREATE_NEW,
                    error);
            if (id != null) {
                events.postValue(new SingleEvent<>("imported:" + id));
            } else {
                events.postValue(new SingleEvent<>("error:" + (error[0] == null ? "unknown" : error[0].getMessage())));
            }
        });
    }
}
