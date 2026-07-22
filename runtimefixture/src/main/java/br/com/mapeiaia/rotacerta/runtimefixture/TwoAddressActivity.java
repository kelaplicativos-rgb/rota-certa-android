package br.com.mapeiaia.rotacerta.runtimefixture;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class TwoAddressActivity extends Activity {
    public static final String PICKUP = "Rua das Flores, 120 - Centro, Sao Paulo - SP";
    public static final String DESTINATION = "Avenida Brasil, 900 - Bela Vista, Santo Andre - SP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(72), dp(24), dp(24));
        root.setBackgroundColor(Color.WHITE);

        root.addView(text("Oferta de corrida", 26f, true));
        root.addView(spacer(36));
        root.addView(text("Embarque", 18f, true));
        root.addView(text(PICKUP, 23f, false));
        root.addView(spacer(34));
        root.addView(text("Destino final", 18f, true));
        root.addView(text(DESTINATION, 23f, false));

        setContentView(root);
    }

    private TextView text(String value, float sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(Color.BLACK);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private TextView spacer(int heightDp) {
        TextView view = new TextView(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
