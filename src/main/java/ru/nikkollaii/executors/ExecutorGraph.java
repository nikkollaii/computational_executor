package ru.nikkollaii.executors;

import ru.nikkollaii.bricks.Pair;
import ru.nikkollaii.bricks.command.CommandsGraph;
import ru.nikkollaii.bricks.command.ICommand;
import ru.nikkollaii.bricks.command.Performance;
import ru.nikkollaii.bricks.connectionsgraph.VertexCG;
import ru.nikkollaii.bricks.state.Attainability;
import ru.nikkollaii.bricks.state.State;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;


public class ExecutorGraph implements IExecutor<CommandsGraph> { // CommandsGraph
    private int count = 0;

    public ExecutorGraph() {}

    @Override
    public void exec(CommandsGraph commands, BlockingQueue<Pair<ICommand, Integer>> externalQueue) {
        // использовать  BlockingQueue queue для запуска 1ой! таски, которая отвечает за поток
        // запускает в отдельном потоке выполнение подзадачи (так же и основной)
        // todo
        /*
        * Ставит задачу на выполнение
        * Слушает очередь(BlockingQueue<Pair<Command(or String), Integer(result_work)>>), в которую ЗАПИСИВАЮ НАБЛЮДАТЕЛИ
        * Вносит изменения в граф, на основе результата поток прининимает одно из решений:
        * 1) ждать следующую задачу
        * 2) запуск доступных задач на выполнение
        * 3) перестроить граф (нужно решить, что делать с запушенными процессами)
        * */
        // Создаётся поток в котором работает очередь з

        Callable<Void> callable = () -> {
//            commands.getGraph().getCommands().stream()
//                    .filter(c->c.getRuntime().equals(Performance.TRAJECTORY))
//                    .forEach(System.out::println);

            BlockingQueue<Pair<ICommand, Integer>> internalQueue = new LinkedBlockingQueue<>();
            do {
                commands.getGraph().getVertexCommands().stream()
                        .filter(c -> c.getObject().getRuntime().equals(Performance.TRAJECTORY))
                        .filter(ExecutorGraph::canStart)
                        .forEach(c -> {
                            System.out.println("START COMMAND: " + c.getObject());
                            c.getObject().exec(internalQueue);
                            c.getObject().setRuntime(Performance.RUNNING);
                            ++count;
                            System.out.println("++ " + count);
                        });
                try {
                    Pair<ICommand, Integer> res = internalQueue.take();
                    --count;
                    System.out.println("-- " + count);
                    // TODO check correct result
                    if (res.getValue() != 0) {
                        res.getKey().setRuntime(Performance.PERFORMED_INCORRECT);
                        // TODO в случае ошибки не нужно ждать всех, вернуть в ожидании остальные задачи (RUNNING)
                        while (count != 0) {
                            try {
                                res = internalQueue.take();
                                if (res.getValue() != 0) {
                                    res.getKey().setRuntime(Performance.PERFORMED_INCORRECT);
                                } else {
                                    res.getKey().setRuntime(Performance.PERFORMED_CORRECT);
                                    for (VertexCG<State, ICommand> out: commands.getGraph()
                                            .getVertexCommand(res.getKey()).getOut()) {
                                        out.getObject().setAttainability(Attainability.ACHIEVED);
                                    }
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        externalQueue.put(new Pair<>(commands, 1));
                        return null;
                    } else {
                        res.getKey().setRuntime(Performance.PERFORMED_CORRECT);
                        for (VertexCG<State, ICommand> out: commands.getGraph().
                                getVertexCommand(res.getKey()).getOut()) {
                            out.getObject().setAttainability(Attainability.ACHIEVED);
                        }
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (! isFinish(commands));

            externalQueue.put(new Pair<>(commands, 0));
            return null;
        };
        // TODO использовать пул потоков! (общий для всех потоков)
        FutureTask<Void> task = new FutureTask<Void>(callable);
        Thread t = new Thread(task);
        t.start();
        commands.setRuntime(Performance.RUNNING);
    }

    @Override
    public boolean isAvailable() {
        // TODO продумать приемлимую стратегию
        return true;
    }

    @Override
    public float confidence() {
        //TODO пробежать по графу, и посчитать произведение confidence
        return 1;
    }

    @Override
    public float overheads(CommandsGraph command) {
        // TODO пробежать по графу, и посчитать сумму overheads
        return 1;
    }

    private static boolean canStart(VertexCG<ICommand, State> stateVertexCG) {
        for (VertexCG<State, ICommand> in: stateVertexCG.getIn()) {
            if (!in.getObject().getAttainability().equals(Attainability.ACHIEVED)) {
                return false;
            }
        }
        return true;
    }

    private boolean isFinish(CommandsGraph commands) {
        System.out.println("IS FINISH " + commands.getGraph().getVertexCommands().stream().filter(c -> c.getObject().getRuntime().equals(Performance.TRAJECTORY)).count());

        return commands.getGraph().getVertexCommands().stream()
                .filter(c -> c.getObject().getRuntime().equals(Performance.TRAJECTORY)).count() == 0;
    }
}
