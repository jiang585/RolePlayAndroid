package com.example.roleplaychat.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.roleplaychat.R;
import com.example.roleplaychat.domain.model.ApiProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置档案列表适配器：点击载入表单编辑，"设为当前"切换启用，长按删除。
 */
public class ApiProfileAdapter extends RecyclerView.Adapter<ApiProfileAdapter.ProfileViewHolder> {

    public interface Listener {
        void onProfileClick(ApiProfile profile);

        void onProfileActivateClick(ApiProfile profile);

        void onProfileLongClick(ApiProfile profile);
    }

    private final Listener listener;
    private final List<ApiProfile> items = new ArrayList<>();
    @Nullable
    private String activeId;

    public ApiProfileAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<ApiProfile> profiles, @Nullable String newActiveId) {
        items.clear();
        if (profiles != null) {
            items.addAll(profiles);
        }
        this.activeId = newActiveId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_api_profile, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        ApiProfile profile = items.get(position);
        boolean active = profile.getId().equals(activeId);
        holder.name.setText(profile.getName());
        holder.summary.setText(summaryOf(profile));
        if (active) {
            holder.activate.setText(R.string.settings_profile_active);
            holder.activate.setTextColor(holder.activate.getContext()
                    .getColor(R.color.text_disabled));
            holder.activate.setOnClickListener(null);
            holder.activate.setClickable(false);
        } else {
            holder.activate.setText(R.string.settings_profile_activate);
            holder.activate.setTextColor(holder.activate.getContext()
                    .getColor(R.color.brand_primary));
            holder.activate.setOnClickListener(v -> listener.onProfileActivateClick(profile));
        }
        holder.itemView.setOnClickListener(v -> listener.onProfileClick(profile));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onProfileLongClick(profile);
            return true;
        });
    }

    private static String summaryOf(ApiProfile profile) {
        String providerName = providerLabel(profile);
        return providerName + " · " + profile.getConfig().getModel() + "\n"
                + profile.getConfig().getBaseUrl();
    }

    private static String providerLabel(ApiProfile profile) {
        switch (profile.getConfig().getProvider()) {
            case DEEPSEEK:
                return "DeepSeek";
            case OPENCODE_GO:
                return "OpenCode Go";
            case OPENAI_COMPATIBLE:
            default:
                return "OpenAI 兼容";
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView summary;
        final TextView activate;

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_profile_name);
            summary = itemView.findViewById(R.id.tv_profile_summary);
            activate = itemView.findViewById(R.id.btn_profile_activate);
        }
    }
}
