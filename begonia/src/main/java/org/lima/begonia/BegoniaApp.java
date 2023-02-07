package org.lima.begonia;

import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
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
    double prev = 0;
    private SimpleDoubleProperty valueProperty;
    Runnable toRun;
    Thread bg;

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
                            var result = globalBuffer.stream().mapToLong(Byte::longValue).sum();
                            var change = (result - prev) / prev * 100;
                            valueProperty.set(change);
                            prev = result;
                            System.out.println(String.format("Change: [%6.2f]; Raw:[%d]", change, result));
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
        FlowGridPane pane = new FlowGridPane(1, 1, sparkLineTile);
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