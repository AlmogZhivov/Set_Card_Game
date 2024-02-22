package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    private Thread dealerThread;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;
    /**
     * True iff the dealer has changed the table
     */
    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    /**
     * Dealer's lock
     */
    public final Object dealerLock;

    public final int setSize;

    private final long maxPlayerToCheckAtOnce;

    private final long clockTick;

    private BlockingQueue<Player> playersToCheck;

    private long timeNotToSleep;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        dealerLock = new Object();
        this.setSize = env.config.featureSize;
        this.clockTick = 1000;
        this.maxPlayerToCheckAtOnce = this.calculateMaxPlayersToCheckAtOnce();
        this.playersToCheck = new ArrayBlockingQueue<>(players.length);
        this.timeNotToSleep = 0;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        this.dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        // The dealer must activate player's threads
        for (Player player : players) {
            Thread playerThread = new Thread(player, player.id + " ");
            playerThread.start();
        }
        placeCardsOnTable();
        while (!shouldFinish()) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
        }
        // announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!shouldFinish() && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            // placeCardsOnTable();
            // env.logger.info("thread " + Thread.currentThread().getName() + "
            // reshuffleTime - currentTimeMillis " +(reshuffleTime -
            // System.currentTimeMillis()));
            // env.logger.info("thread " + Thread.currentThread().getName() + " should
            // finish " + shouldFinish());
            while ((!shouldFinish() && !deck.isEmpty() && !areAvailableSets())
                    || reshuffleTime - System.currentTimeMillis() <= 0) {
                removeAllCardsFromTable();
                placeCardsOnTable();
                updateTimerDisplay(true);
            }
            if (shouldFinish()) {
                terminate();
                announceWinners();
            }
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        synchronized (dealerLock) {
            for (int i = players.length - 1; i >= 0; i--) {
                players[i].terminate();
            }
            this.terminate = true;
            dealerThread.interrupt();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        synchronized (dealerLock) {
            return terminate || (!areAvailableSets() && deck.isEmpty()) ||
                    (env.util.findSets(deck, 1).size() == 0 && !areAvailableSets());
        }
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {

        env.logger.info("thread " + Thread.currentThread().getName() + " removing cards from table");

        long playersLeft = maxPlayerToCheckAtOnce;
        // env.logger.info("thread " + Thread.currentThread().getName() + " playersLeft
        // " + playersLeft);
        while (playersLeft > 0 && !playersToCheck.isEmpty()) {
            Player player = playersToCheck.remove();
            env.logger.info("thread " + Thread.currentThread().getName() + " checking player " + player.id);
            if (table.checkAndRemoveSet(player.id, this)) {
                env.logger.info("thread " + Thread.currentThread().getName() + " pointing player " + player.id);
                player.point();
                this.resetTimer();
                playersLeft = playersLeft - 1;
                timeNotToSleep = timeNotToSleep + setSize * env.config.tableDelayMillis;
            } else {
                env.logger.info("thread " + Thread.currentThread().getName() + " penalizing player " + player.id);
                player.penalty();
            }
        }

        placeCardsOnTable();
    }

    // CHECK IF THE SET IS VALID, IF IT IS SO SET 'isValid' to 1, else 0 using
    // setIsValid metod
    // GET THE SLOT AND THE PLAYER FROM 'boardPlayers' 'boardSlots' amd check if
    // boardSlots.length > 2
    // done implement

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // synchronized(dealerLock){
        // hasChanged = true;
        List<Integer> emptySlots = table.getEmptySlots();
        if (emptySlots == null || emptySlots.isEmpty())
            return;

        for (int i : emptySlots) {
            if (!deck.isEmpty())
                table.placeCard(deck.remove(0), i);
        }
        // }
        // done implement
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        //long sleepTime = clockTick; // one seconed
        int minSleepTime = 10;
        long timeLeft = reshuffleTime - System.currentTimeMillis();
        long sleepTime = timeLeft%clockTick;
        sleepTime = Math.min(sleepTime, clockTick); // sleep time should not be bigger than clockTick
        if (timeLeft > env.config.turnTimeoutWarningMillis) {
            //sleepTime = sleepTime - timeNotToSleep;
        } else {
            sleepTime = 10;
            env.logger.info("thread " + Thread.currentThread().getName() + "timeLeft is small");
            env.logger.info("thread " + Thread.currentThread().getName() + "timeLeft: " + timeLeft);
        }
        try {
            Thread.sleep(Math.max(sleepTime, minSleepTime)); // should not sleep less than 10 ms
        } catch (InterruptedException e) {
        }
        timeNotToSleep = 0;
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset && !shouldFinish())
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        long timeLeft = reshuffleTime - System.currentTimeMillis();
        if (timeLeft > env.config.turnTimeoutWarningMillis)
            env.ui.setCountdown(Math.max(timeLeft, 0), false);
        else
            env.ui.setCountdown(Math.max(timeLeft, 0), true);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        env.logger.info("thread " + Thread.currentThread().getName() + " Dealer remove all cards");
        List<Integer> cardsToRemove = table.removeAllCards();
        // remove cards from table
        for (int card : cardsToRemove) {
            deck.add((Integer) card);
        }
        // shuffle the cards again after removal
        Collections.shuffle(deck);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {

        if(!terminate)
            return;

        List<Integer> maxPlayer = new LinkedList<Integer>();
        int maximum = players[0].score();
        // Find the maximum score
        for (int i = 1; i < players.length; i++) {
            if (players[i].score() > maximum)
                maximum = players[i].score();
        }
        // Find the player with the maximum score or the 2 players with tie
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == maximum)
                maxPlayer.add(players[i].id);
        }
        // List to array convertion
        int[] winners = new int[maxPlayer.size()];
        for (int i = 0; i < winners.length; i++) {
            winners[i] = maxPlayer.get(i);
        }
        env.ui.announceWinner(winners);
    }

    private boolean areAvailableSets() {
        // returns true if there is a set avialable on the table
        return table.areAvailableSets();
    }

    // 'wakes up' the dealer. notifies its lock
    public void wakeUp() {
        env.logger.info("thread " + Thread.currentThread().getName() + " waking up dealer");
        dealerThread.interrupt();
    }

    public void resetTimer() {
        updateTimerDisplay(true);
    }

    public boolean testSet(int[] cards) {
        return env.util.testSet(cards);
    }

    private long calculateMaxPlayersToCheckAtOnce() {
        long timeToRemoveSet = this.setSize * env.config.tableDelayMillis;
        //env.logger.info("thread " + Thread.currentThread().getName() + " timeToRemoveSet " + timeToRemoveSet);
        // timeToRemoveSet*playersToCheckAtOnce should be < clockTick
        long output = clockTick / timeToRemoveSet;
        //env.logger.info("thread " + Thread.currentThread().getName() + " output " + output);
        return output;
    }

    public void checkPlayer(Player player) {
        env.logger
                .info("thread " + Thread.currentThread().getName() + " adding player " + player.id + " to check queue");
        try {
            playersToCheck.put(player);
            wakeUp();
        } catch (InterruptedException e) {
        }
    }

}
