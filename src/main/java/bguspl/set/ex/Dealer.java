package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.List;
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
    private boolean hasChanged = false;
    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        this.dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        hasChanged = true;
        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        hasChanged = true;
        // TODO implement


        // After the dealer has placed the cards, we gonna change back the status to false
        hasChanged = false;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset && !shouldFinish())
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        long timeLeft = reshuffleTime - System.currentTimeMillis();
        if (timeLeft > env.config.turnTimeoutWarningMillis)
            env.ui.setCountdown(timeLeft, false);
        else
            env.ui.setCountdown(timeLeft, true);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        hasChanged = true;
        // TODO implement
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
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
        // From list to array
        int[] winners = new int[maxPlayer.size()];
        for (int i = 0; i < winners.length; i++) {
            winners[i] = maxPlayer.get(i);
        }
        env.ui.announceWinner(winners);
    }

    public boolean hasChanged() {
        return hasChanged;
    }
}
