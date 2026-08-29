package com.example.roleplaychat.ui.chat;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.roleplaychat.R;
import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.domain.model.Appearance;
import com.example.roleplaychat.domain.model.ChatMessage;
import com.example.roleplaychat.util.JsonUtils;

import java.io.File;
import java.util.List;

/**
 * 聊天消息适配器（架构文档 §10.3）：稳定消息 ID + 多 ViewType。
 * ViewType：MINE / THEIRS / NARRATION / EVENT / DATE_SEPARATOR。
 * 流式更新节流在 UI 层控制；本适配器通过 DiffUtil 只更新变化项。
 */
public class ChatMessageAdapter extends ListAdapter<ChatListItem, RecyclerView.ViewHolder> {

    public interface AvatarLongClickListener {
        void onAvatarLongClick(String displayName);
    }

    private static final int TYPE_MINE = 0;
    private static final int TYPE_THEIRS = 1;
    private static final int TYPE_NARRATION = 2;
    private static final int TYPE_EVENT = 3;
    private static final int TYPE_DATE = 4;

    private Appearance appearance;
    private final AvatarLongClickListener avatarLongClickListener;

    public ChatMessageAdapter(AvatarLongClickListener avatarLongClickListener) {
        super(DIFF);
        this.avatarLongClickListener = avatarLongClickListener;
    }

    public void setAppearance(@Nullable Appearance appearance) {
        this.appearance = appearance;
    }

    private static final DiffUtil.ItemCallback<ChatListItem> DIFF =
            new DiffUtil.ItemCallback<ChatListItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChatListItem oldItem, @NonNull ChatListItem newItem) {
                    if (oldItem.getKind() != newItem.getKind()) {
                        return false;
                    }
                    if (ChatListItem.Kind.DATE_SEPARATOR.equals(oldItem.getKind())) {
                        return java.util.Objects.equals(oldItem.getDateLabel(), newItem.getDateLabel());
                    }
                    return oldItem.getMessage().getId().equals(newItem.getMessage().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChatListItem oldItem, @NonNull ChatListItem newItem) {
                    if (ChatListItem.Kind.DATE_SEPARATOR.equals(oldItem.getKind())) {
                        return java.util.Objects.equals(oldItem.getDateLabel(), newItem.getDateLabel());
                    }
                    ChatMessage oldMessage = oldItem.getMessage();
                    ChatMessage newMessage = newItem.getMessage();
                    return java.util.Objects.equals(oldMessage.getContent(), newMessage.getContent())
                            && java.util.Objects.equals(oldMessage.getStatus(), newMessage.getStatus())
                            && oldMessage.getSequence() == newMessage.getSequence();
                }
            };

    @Override
    public int getItemViewType(int position) {
        ChatListItem item = getItem(position);
        if (item.getKind() == ChatListItem.Kind.DATE_SEPARATOR) {
            return TYPE_DATE;
        }
        ChatMessage message = item.getMessage();
        if (message.getType() == ChatMessage.Type.NARRATION) {
            return TYPE_NARRATION;
        }
        if (message.getType() == ChatMessage.Type.SYSTEM_EVENT) {
            return TYPE_EVENT;
        }
        return message.getSide() == ChatMessage.Side.MINE ? TYPE_MINE : TYPE_THEIRS;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_MINE:
                return new MineViewHolder(inflater.inflate(R.layout.item_message_mine, parent, false));
            case TYPE_NARRATION:
                return new NarrationViewHolder(inflater.inflate(R.layout.item_message_narration, parent, false));
            case TYPE_EVENT:
                return new EventViewHolder(inflater.inflate(R.layout.item_message_event, parent, false));
            case TYPE_DATE:
                return new DateViewHolder(inflater.inflate(R.layout.item_date_separator, parent, false));
            case TYPE_THEIRS:
            default:
                return new TheirsViewHolder(inflater.inflate(R.layout.item_message_theirs, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatListItem item = getItem(position);
        if (holder instanceof MineViewHolder) {
            bindMine((MineViewHolder) holder, item.getMessage());
        } else if (holder instanceof TheirsViewHolder) {
            bindTheirs((TheirsViewHolder) holder, item.getMessage());
        } else if (holder instanceof NarrationViewHolder) {
            bindNarration((NarrationViewHolder) holder, item.getMessage());
        } else if (holder instanceof EventViewHolder) {
            bindEvent((EventViewHolder) holder, item.getMessage());
        } else if (holder instanceof DateViewHolder) {
            ((DateViewHolder) holder).label.setText(item.getDateLabel());
        }
    }

    private void bindMine(MineViewHolder holder, ChatMessage message) {
        holder.nickname.setText(message.getSenderDisplayName() == null ? "" : message.getSenderDisplayName());
        holder.content.setText(message.getContent());
        if (appearance != null) {
            holder.content.setTextColor(parseColor(appearance.getTextColor(),
                    holder.content.getContext().getColor(R.color.s9_my_text)));
            // 气泡底色保留 drawable 设计色；用户自定义 bubbleColor 时覆盖
            if (appearance.getBubbleColor() != null
                    && !"#FFB8E6C1".equalsIgnoreCase(appearance.getBubbleColor())
                    && !"#FF95EC69".equalsIgnoreCase(appearance.getBubbleColor())) {
                holder.content.setBackgroundColor(parseColor(appearance.getBubbleColor(),
                        holder.content.getContext().getColor(R.color.s9_my_bubble)));
            }
        }
        loadAvatar(holder.avatar, message.getSenderAvatarRef());
        bindAvatarLongClick(holder.avatar, message);
    }

    private void bindTheirs(TheirsViewHolder holder, ChatMessage message) {
        holder.nickname.setText(TextUtils.isEmpty(message.getSenderDisplayName())
                ? "未知角色" : message.getSenderDisplayName());
        if (appearance != null) {
            holder.nickname.setTextColor(parseColor(appearance.getNicknameColor(),
                    holder.nickname.getContext().getColor(R.color.s9_npc_name)));
            // NPC 气泡底色保留 drawable 设计色（米白+边框）；仅自定义色时覆盖
            if (appearance.getBubbleColor() != null
                    && !"#FFB8E6C1".equalsIgnoreCase(appearance.getBubbleColor())
                    && !"#FF95EC69".equalsIgnoreCase(appearance.getBubbleColor())) {
                holder.content.setBackgroundColor(parseColor(appearance.getBubbleColor(),
                        holder.content.getContext().getColor(R.color.s9_npc_bubble)));
            }
            holder.content.setTextColor(parseColor(appearance.getTextColor(),
                    holder.content.getContext().getColor(R.color.s9_npc_text)));
        } else {
            holder.content.setBackgroundColor(holder.content.getContext()
                    .getColor(R.color.s9_npc_bubble));
        }
        holder.content.setText(message.getContent());
        loadAvatar(holder.avatar, message.getSenderAvatarRef());
        bindAvatarLongClick(holder.avatar, message);
    }

    private void bindAvatarLongClick(ImageView avatar, ChatMessage message) {
        avatar.setOnLongClickListener(v -> {
            if (avatarLongClickListener == null || TextUtils.isEmpty(message.getSenderDisplayName())) {
                return false;
            }
            avatarLongClickListener.onAvatarLongClick(message.getSenderDisplayName());
            return true;
        });
    }

    private void bindNarration(NarrationViewHolder holder, ChatMessage message) {
        holder.content.setText(message.getContent());
    }

    private void bindEvent(EventViewHolder holder, ChatMessage message) {
        holder.content.setText(message.getContent());
    }

    private void loadAvatar(ImageView imageView, @Nullable String avatarRef) {
        if (TextUtils.isEmpty(avatarRef)) {
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
            return;
        }
        File file = ((RolePlayChatApp) imageView.getContext().getApplicationContext())
                .container().assetStore.resolve(avatarRef);
        if (file != null) {
            Glide.with(imageView.getContext())
                    .load(file)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .circleCrop()
                    .into(imageView);
        } else {
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    private int parseColor(String argbHex, int fallback) {
        if (argbHex == null || argbHex.isEmpty()) {
            return fallback;
        }
        try {
            return (int) Long.parseLong(argbHex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---------- ViewHolders ----------

    static class MineViewHolder extends RecyclerView.ViewHolder {
        final TextView content;
        final TextView nickname;
        final ImageView avatar;

        MineViewHolder(@NonNull View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.tv_message_content);
            nickname = itemView.findViewById(R.id.tv_nickname);
            avatar = itemView.findViewById(R.id.iv_avatar);
        }
    }

    static class TheirsViewHolder extends RecyclerView.ViewHolder {
        final TextView nickname;
        final TextView content;
        final ImageView avatar;

        TheirsViewHolder(@NonNull View itemView) {
            super(itemView);
            nickname = itemView.findViewById(R.id.tv_nickname);
            content = itemView.findViewById(R.id.tv_message_content);
            avatar = itemView.findViewById(R.id.iv_avatar);
        }
    }

    static class NarrationViewHolder extends RecyclerView.ViewHolder {
        final TextView content;

        NarrationViewHolder(@NonNull View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.tv_narration_content);
        }
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        final TextView content;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.tv_event_content);
        }
    }

    static class DateViewHolder extends RecyclerView.ViewHolder {
        final TextView label;

        DateViewHolder(@NonNull View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.tv_date_label);
        }
    }
}
