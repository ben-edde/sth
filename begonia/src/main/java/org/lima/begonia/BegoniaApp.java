package org.lima.begonia;

import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import eu.hansolo.tilesfx.colors.Bright;
import eu.hansolo.tilesfx.colors.Dark;
import eu.hansolo.tilesfx.tools.FlowGridPane;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Scene;
import javafx.scene.paint.Stop;
import javafx.stage.Stage;
import org.apache.commons.lang3.ArrayUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class BegoniaApp extends Application
{
    private static final double TILE_WIDTH = 500;
    private static final double TILE_HEIGHT = 500;

    private long lastTimerCall;
    private AnimationTimer timer;

    private Tile sparkLineTile;
    private Tile barGaugeTile;
    double prev = 0;
    private SimpleDoubleProperty valueProperty;
    Runnable toRun;
    Thread bg;

    float lastPeak = 0f;

    final ArrayDeque<Byte> globalBuffer = new ArrayDeque<>();

    public static void main(String[] args)
    {
        launch();
    }

    @Override
    public void init()
    {
        valueProperty = new SimpleDoubleProperty(0);
        sparkLineTile = TileBuilder.create().skinType(Tile.SkinType.SPARK_LINE).prefSize(TILE_WIDTH, TILE_HEIGHT).title("Audio Input").gradientStops(new Stop(0, Tile.GREEN), new Stop(0.5, Tile.YELLOW), new Stop(1.0, Tile.RED)).strokeWithGradient(true).smoothing(false).minValue(-100).maxValue(100).build();
        sparkLineTile.valueProperty().bind(valueProperty);

        barGaugeTile = TileBuilder.create()
                .skinType(Tile.SkinType.BAR_GAUGE)
                .prefSize(TILE_WIDTH, TILE_HEIGHT)
                .minValue(0)
                .maxValue(100)
                .startFromZero(true)
                .threshold(100)
                .thresholdVisible(true)
                .title("Audio Input")
                .gradientStops(new Stop(0, Bright.BLUE),
                        new Stop(0.1, Bright.BLUE_GREEN),
                        new Stop(0.2, Bright.GREEN),
                        new Stop(0.3, Bright.GREEN_YELLOW),
                        new Stop(0.4, Bright.YELLOW),
                        new Stop(0.5, Bright.YELLOW_ORANGE),
                        new Stop(0.6, Bright.ORANGE),
                        new Stop(0.7, Bright.ORANGE_RED),
                        new Stop(0.8, Bright.RED),
                        new Stop(1.0, Dark.RED))
                .strokeWithGradient(true)
                .animated(true)
                .build();


        lastTimerCall = System.nanoTime();
        timer = new AnimationTimer()
        {
            @Override
            public void handle(long now)
            {
                if (now > lastTimerCall + 1_000_000_000L)
                {
                    synchronized (globalBuffer)
                    {
                        if (globalBuffer.size() != 0)
                        {
                            var sampleSize = globalBuffer.size() / 2;

                            float[] samples = new float[sampleSize];

                            for (int i = 0; i < sampleSize; ++i)
                            {
                                int sample = 0;
                                sample |= globalBuffer.removeFirst() & 0xFF;
                                sample |= globalBuffer.removeFirst() << 8;
                                samples[i] = sample / 32767f;
                            }

                            float rms = 0f;
                            float peak = 0f;
                            for (float sample : samples)
                            {

                                float abs = Math.abs(sample);
                                if (abs > peak)
                                {
                                    peak = abs;
                                }

                                rms += sample * sample;
                            }

                            rms = (float) Math.sqrt(rms / samples.length);

                            if (lastPeak > peak)
                            {
                                peak = lastPeak * 0.875f;
                            }

                            lastPeak = peak;

                            barGaugeTile.setThreshold(peak * 100);
                            barGaugeTile.setValue(rms * 100);
                            System.out.println(String.format("peak: [%6.2f]; rms:[%6.2f]", peak, rms));
                            globalBuffer.clear();
                        }
                    }
                    lastTimerCall = now;
                }
            }
        };

        toRun = () ->
        {
            ArrayDeque<Byte> localBuffer = new ArrayDeque<>();
            TimerTask task = new TimerTask()
            {
                public void run()
                {
                    Byte[] tmp;
                    synchronized (localBuffer)
                    {
                        tmp = localBuffer.toArray(new Byte[0]);
                        localBuffer.clear();
                    }
                    synchronized (globalBuffer)
                    {
                        globalBuffer.addAll(Arrays.asList(tmp));
                    }
                }
            };
            Timer timer = new Timer("Timer");
            long delay = 100L;
            timer.scheduleAtFixedRate(task, delay, delay);
            try
            {
                AudioFormat format = new AudioFormat(44100, 16, 2, true, true);
                DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine targetLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
                targetLine.open(format);
                targetLine.start();
                int numBytesRead = 0;
                byte[] targetData = new byte[targetLine.getBufferSize() / 5];
                while (numBytesRead != -1)
                {
                    numBytesRead = targetLine.read(targetData, 0, targetData.length);
                    synchronized (localBuffer)
                    {
                        localBuffer.addAll(Arrays.asList(ArrayUtils.toObject(targetData)));
                    }
                }
                targetLine.stop();
                targetLine.close();
            } catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    public void start(Stage stage) throws IOException
    {
        FlowGridPane pane = new FlowGridPane(1, 1, barGaugeTile);
        Scene scene = new Scene(pane);
        stage.setTitle("Begonia");
        stage.setScene(scene);
        bg = new Thread(toRun);
        stage.show();
        timer.start();
        bg.start();
    }

    @Override
    public void stop()
    {
        bg.stop();
        timer.stop();
        System.exit(0);
    }
}