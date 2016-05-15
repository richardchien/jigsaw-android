package im.r_c.android.jigsaw;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.theartofdev.edmodo.cropper.CropImage;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import im.r_c.android.jigsaw.util.IOUtils;
import im.r_c.android.jigsaw.util.L;
import im.r_c.android.jigsaw.util.UIUtils;
import me.drakeet.mailotto.Mail;
import me.drakeet.mailotto.Mailbox;
import me.drakeet.mailotto.OnMailReceived;

/**
 * Jigsaw
 * Created by richard on 16/5/15.
 */
public class GameActivity extends AppCompatActivity {
    private static final String TAG = "GameActivity";
    public static final int SPAN_COUNT = 3;
    public static final int BLANK_BRICK = 8;
//    public static final int BLANK_BRICK = 3;
    public static final int[][] GOAL_STATUS = {{0, 1, 2}, {3, 4, 5}, {6, 7, BLANK_BRICK}};
//    public static final int[][] GOAL_STATUS = {{0, 1}, {2, BLANK_BRICK}};
    public static final int MAIL_GAME_STARTED = 100;
    public static final int MAIL_STEP_MOVED = 101;
    public static final int MAIL_GAME_WON = 102;

    private Bitmap mFullBitmap = null;
    private Bitmap[] mBitmapBricks = new Bitmap[SPAN_COUNT * SPAN_COUNT];
    private Handler mHandler = new Handler();
    private Timer mTimer = null;
    private long mStartTime = 0;
    private int mStepCount = 0;

    private TextView mTvTime;
    private TextView mTvStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        mTvTime = (TextView) findViewById(R.id.tv_time);
        mTvStep = (TextView) findViewById(R.id.tv_step);

        Mailbox.getInstance().atHome(this);

        startActivityForNewPicture();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Mailbox.getInstance().leave(this);
    }

    private void startActivityForNewPicture() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE: {
                if (resultCode == RESULT_OK) {
                    CropImage.activity(data.getData())
                            .setActivityTitle("裁剪")
                            .setAspectRatio(1, 1)
                            .setFixAspectRatio(true)
                            .start(this);
                }
                break;
            }
            case CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE: {
                if (resultCode == RESULT_OK) {
                    CropImage.ActivityResult result = CropImage.getActivityResult(data);
                    handleCropResult(result);
                }
                break;
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void handleCropResult(CropImage.ActivityResult result) {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(result.getUri()));

            // Scale the bitmap to a proper size, avoiding waste of memory
            View container = findViewById(R.id.fl_board_container);
            assert container != null;
            int paddingHorizontal = container.getPaddingLeft() + container.getPaddingRight();
            int paddingVertical = container.getPaddingTop() + container.getPaddingBottom();
            mFullBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    container.getWidth() - paddingHorizontal,
                    container.getHeight() - paddingVertical,
                    false);

            cutBitmapIntoPieces();
            mBitmapBricks[SPAN_COUNT * SPAN_COUNT - 1] = BitmapFactory.decodeResource(getResources(), R.drawable.blank_brick);

            startNewGame();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Delete the cache file CropImage generated
        IOUtils.deleteFile(result.getUri().getPath());
    }

    private void cutBitmapIntoPieces() {
        int dividerWidth = (int) getResources().getDimension(R.dimen.brick_divider_width);
        int brickWidth = (mFullBitmap.getWidth() - dividerWidth * (SPAN_COUNT - 1)) / SPAN_COUNT;
        int brickHeight = (mFullBitmap.getHeight() - dividerWidth * (SPAN_COUNT - 1)) / SPAN_COUNT;
        for (int i = 0; i < SPAN_COUNT; i++) {
            for (int j = 0; j < SPAN_COUNT; j++) {
                mBitmapBricks[i * SPAN_COUNT + j] = Bitmap.createBitmap(
                        mFullBitmap,
                        j * (brickWidth + dividerWidth),
                        i * (brickHeight + dividerWidth),
                        brickWidth, brickHeight);
            }
        }
    }

    private void startNewGame() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fl_board_container, GameFragment.newInstance(mBitmapBricks, GOAL_STATUS))
                .commit();
    }

    @OnMailReceived
    public void onMailReceived(Mail mail) {
        if (mail.from == GameFragment.class) {
            switch ((int) mail.content) {
                case MAIL_GAME_STARTED:
                    L.d(TAG, "Game started");
                    onGameStarted();
                    break;
                case MAIL_STEP_MOVED:
                    L.d(TAG, "Moved");
                    onStepMoved();
                    break;
                case MAIL_GAME_WON:
                    L.d(TAG, "Game won");
                    onGameWon();
                    break;
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void onGameStarted() {
        mStepCount = 0;
        mTvStep.setText(String.valueOf(mStepCount));
        mTvTime.setText("00:00");

        mStartTime = System.currentTimeMillis();
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @SuppressLint("SimpleDateFormat")
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        long nowTime = System.currentTimeMillis();
                        Date span = new Date(nowTime - mStartTime);
                        SimpleDateFormat format = new SimpleDateFormat("mm:ss");
                        mTvTime.setText(format.format(span));
                    }
                });
            }
        }, 0, 1000);
    }

    private void onStepMoved() {
        mStepCount++;
        mTvStep.setText(String.valueOf(mStepCount));
    }

    private void onGameWon() {
        mTimer.cancel();
        mTimer.purge();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fl_board_container, WinFragment.newInstance(mFullBitmap))
                        .commit();
                UIUtils.toast(GameActivity.this, "You won!", true);
            }
        }, 500);
    }

    public void changePicture(View view) {
        startActivityForNewPicture();
    }

    public void restart(View view) {
        startNewGame();
    }

    public void lookUpOriginalPicture(View view) {
        View alertView = View.inflate(this, R.layout.dialog_loop_up, null);
        ImageView imageView = (ImageView) alertView.findViewById(R.id.iv_image);
        imageView.setImageBitmap(mFullBitmap);
        new AlertDialog.Builder(this)
                .setView(alertView)
                .show();
    }
}
