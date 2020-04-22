package protect.babymonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.LinkedList;

public class VolumeView extends View {
    private static final int MAX_HISTORY = 10_000;
    private double volume;
    private double maxVolume;
    private Paint paint;
    private LinkedList<Double> volumeHistory;

    public VolumeView(Context context) {
        super(context);
        init();
    }

    public VolumeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VolumeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        volume = 0;
        maxVolume = 0.25;
        paint = new Paint();
        volumeHistory = new LinkedList<>();
        paint.setColor(Color.rgb(255, 127, 0));
    }

    public void onAudioData(byte[] data) {
        double sum = 0;
        for (int i = 0; i < data.length; i++) {
            double rel = data[i] / ((double)128);
            sum += Math.pow(rel, 2);
        }
        volume = sum / data.length;
        if (volume > maxVolume) {
            maxVolume = volume;
        }
        volumeHistory.addLast(volume);
        while (volumeHistory.size() > MAX_HISTORY) {
            volumeHistory.removeFirst();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int height = canvas.getHeight();
        int width = canvas.getWidth();
        double relativeBrightness = 0;
        double normalizedVolume = volume / maxVolume;
        relativeBrightness = Math.max(0.3, normalizedVolume);
        int blue;
        int rest;
        if (relativeBrightness > 0.5) {
            blue = 255;
            rest = (int) (2 * 255 * (relativeBrightness - 0.5));
        } else {
            blue = (int) (255 * (relativeBrightness - 0.2) / 0.3);
            rest = 0;
        }
        int rgb = Color.rgb(rest, rest, blue);
        canvas.drawColor(rgb);
        double margins = height * 0.1;
        double graphHeight = height - 2*margins;
        int leftMost = Math.max(0, volumeHistory.size() - width);
        int yPrev = (int) (graphHeight - margins);
        for (int i = leftMost; i < volumeHistory.size() && i - leftMost < width; i++) {
            int xNext = i - leftMost;
            int yNext = (int) (margins + graphHeight - volumeHistory.get(i) / maxVolume * (graphHeight));
            int xPrev;
            if (i == leftMost) {
                xPrev = xNext;
            } else {
                xPrev = xNext - 1;
            }
            if (i == leftMost && i > 0){
                yPrev = (int) (margins + graphHeight - volumeHistory.get(i-1) / maxVolume * (graphHeight));
            }
            canvas.drawLine(xPrev, yPrev, xNext, yNext, paint);
            yPrev = yNext;

        }

    }
}
