package com.example.roleplaychat.ui.character;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.domain.model.CharacterProfile;

/**
 * 角色列表适配器（用于剧本详情页与角色管理页）。
 */
public class CharacterListAdapter extends ListAdapter<CharacterProfile, CharacterListAdapter.CharacterViewHolder> {

    public interface Listener {
        void onCharacterClick(CharacterProfile character);
    }

    /** 长按回调：用于触发删除/停用等管理操作。 */
    public interface OnCharacterLongClickListener {
        void onCharacterLongClick(CharacterProfile character);
    }

    private final Listener listener;
    @androidx.annotation.Nullable
    private final OnCharacterLongClickListener longClickListener;

    public CharacterListAdapter(Listener listener) {
        this(listener, null);
    }

    public CharacterListAdapter(Listener listener,
                                @androidx.annotation.Nullable OnCharacterLongClickListener longClickListener) {
        super(DIFF);
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    private static final DiffUtil.ItemCallback<CharacterProfile> DIFF =
            new DiffUtil.ItemCallback<CharacterProfile>() {
                @Override
                public boolean areItemsTheSame(@NonNull CharacterProfile oldItem,
                                               @NonNull CharacterProfile newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull CharacterProfile oldItem,
                                                  @NonNull CharacterProfile newItem) {
                    return oldItem.getName().equals(newItem.getName())
                            && oldItem.isEnabled() == newItem.isEnabled()
                            && oldItem.getUpdatedAt() == newItem.getUpdatedAt();
                }
            };

    @NonNull
    @Override
    public CharacterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_character, parent, false);
        return new CharacterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CharacterViewHolder holder, int position) {
        CharacterProfile character = getItem(position);
        holder.name.setText(character.getName());
        String subtitle = buildSubtitle(character);
        holder.subtitle.setText(subtitle);
        holder.enabledIndicator.setVisibility(character.isEnabled() ? View.VISIBLE : View.GONE);

        String avatarRef = character.getAvatarRef();
        if (avatarRef != null && !avatarRef.isEmpty()) {
            java.io.File file = ((RolePlayChatApp) holder.itemView.getContext().getApplicationContext())
                    .container().assetStore.resolve(avatarRef);
            if (file != null) {
                Glide.with(holder.itemView.getContext())
                        .load(file)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .circleCrop()
                        .into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        } else {
            holder.avatar.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        holder.itemView.setOnClickListener(v -> listener.onCharacterClick(character));
        if (longClickListener != null) {
            holder.itemView.setOnLongClickListener(v -> {
                longClickListener.onCharacterLongClick(character);
                return true;
            });
        }
    }

    private String buildSubtitle(CharacterProfile character) {
        StringBuilder sb = new StringBuilder();
        if (character.getGender() != null && !character.getGender().isEmpty()) {
            sb.append(character.getGender());
        }
        if (character.getAgeText() != null && !character.getAgeText().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(character.getAgeText());
        }
        if (character.getPersonality() != null && !character.getPersonality().isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(character.getPersonality());
        }
        return sb.toString();
    }

    static class CharacterViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView name;
        final TextView subtitle;
        final View enabledIndicator;

        CharacterViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.iv_avatar);
            name = itemView.findViewById(R.id.tv_char_name);
            subtitle = itemView.findViewById(R.id.tv_char_subtitle);
            enabledIndicator = itemView.findViewById(R.id.view_enabled_dot);
        }
    }
}
