package de.quandoo.android2androidaccessory;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import de.quandoo.android2androidaccessory.databinding.ActivityChatBinding;

public abstract class BaseChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;

    protected abstract void sendString(final String string);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityChatBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        binding.sendButton.setOnClickListener(new ButtonClickListener());
        setContentView(view);
    }

    public class ButtonClickListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            final String inputString = binding.inputEdittext.getText().toString();
            if (inputString.length() == 0) {
                return;
            }

            sendString(inputString);
            printLineToUI(getString(R.string.local_prompt) + inputString);
            binding.inputEdittext.setText("");
        }
    }

    protected void printLineToUI(final String line) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.contentText.setText(binding.contentText.getText() + "\n" + line);
            }
        });
    }

}
