package com.example.roleplaychat.ui.script;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.roleplaychat.R;
import com.example.roleplaychat.domain.model.Script;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 剧本列表适配器。
 */
public class ScriptListAdapter extends ListAdapter<Script, ScriptListAdapter.ScriptViewHolder> {

    public interface Listener {
        void onScriptClick(Script script);

        void onScriptLongClick(Script script);
    }

    private final Listener listener;

    public ScriptListAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Script> DIFF = new DiffUtil.ItemCallback<Script>() {
        @Override
        public boolean areItemsTheSame(@NonNull Script oldItem, @NonNull Script newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Script oldItem, @NonNull Script newItem) {
            return oldItem.getName().equals(newItem.getName())
                    && oldItem.getUpdatedAt() == newItem.getUpdatedAt();
        }
    };

    @NonNull
    @Override
    public ScriptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_script, parent, false);
        return new ScriptViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScriptViewHolder holder, int position) {
        Script script = getItem(position);
        holder.name.setText(script.getName());
        holder.oneLine.setText(script.getOneLine() == null || script.getOneLine().isEmpty()
                ? holder.itemView.getContext().getString(R.string.script_detail_no_world)
                : script.getOneLine());
        holder.time.setText(new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(script.getUpdatedAt())));
        holder.itemView.setOnClickListener(v -> listener.onScriptClick(script));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onScriptLongClick(script);
            return true;
        });
    }

    static class ScriptViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView oneLine;
        final TextView time;

        ScriptViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_script_name);
            oneLine = itemView.findViewById(R.id.tv_script_one_line);
            time = itemView.findViewById(R.id.tv_script_time);
        }
    }
}
