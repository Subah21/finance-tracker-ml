package edu.msu.cse476.haidaris.finance_tracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * RecyclerView adapter that displays a list of transactions.
 * Each item shows the description (or category), category label, and amount.
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private JSONArray transactions = new JSONArray();

    /** Replace the data set and refresh the list. */
    public void setTransactions(JSONArray newTransactions) {
        this.transactions = newTransactions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            // Show newest first: reverse the index
            int reverseIndex = transactions.length() - 1 - position;
            JSONObject tx = transactions.getJSONObject(reverseIndex);

            String desc     = tx.optString("description", "");
            String category = tx.optString("category", "");
            double amount   = tx.getDouble("amount");

            holder.txName.setText(desc.isEmpty() ? capitalize(category) : desc);
            holder.txMeta.setText(capitalize(category));
            holder.txAmount.setText(String.format("-$%.2f", amount));
        } catch (Exception e) {
            holder.txName.setText("");
            holder.txMeta.setText("");
            holder.txAmount.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return transactions.length();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txName, txMeta, txAmount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txName   = itemView.findViewById(R.id.txName);
            txMeta   = itemView.findViewById(R.id.txMeta);
            txAmount = itemView.findViewById(R.id.txAmount);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
