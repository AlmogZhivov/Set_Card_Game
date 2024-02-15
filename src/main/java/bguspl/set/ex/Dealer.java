package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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
    //private boolean hasChanged = false;
    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    /**
     * Dealer's lock
     */
    public final Object dealerLock;

    public final int setSize;
     /**
     * Slots of the current round
     */
    // private BlockingQueue<Integer> boardSlots;
    //  /**
    //  * Players of the current round
    //  */
    // private BlockingQueue<Player> boardPlayers;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        dealerLock = new Object();
        this.setSize = env.config.featureSize;
        // this.boardSlots = new LinkedBlockingQueue<>(Integer.MAX_VALUE);
        // this.boardPlayers = new LinkedBlockingQueue<>(Integer.MAX_VALUE);
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
            //placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            //removeAllCardsFromTable();
        }
        //announceWinners();
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
            //placeCardsOnTable(); 
            while (!areAvailableSets() && !deck.isEmpty()) {
                removeAllCardsFromTable();
                placeCardsOnTable();
                updateTimerDisplay(true);
            }
            if (!areAvailableSets() && deck.isEmpty()) {
                terminate();
                announceWinners();
            }
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        synchronized(dealerLock){
            for (int i = players.length - 1; i >= 0; i--) {
                players[i].terminate();
            }
            this.terminate = true;
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        synchronized(dealerLock) {
            return terminate || env.util.findSets(deck, 1).size() == 0 ||
            (!areAvailableSets() && deck.isEmpty()); 
        }
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        synchronized(dealerLock) {
           // hasChanged = true;

            for (Player player : players) {
                if (table.getNumOfTokensOnTable(player.id) == setSize) {
                    int[] tokens = table.getTokens(player.id);
                        if (env.util.testSet(tokens)) {
                            // player chose a legal set
                            for (int token : tokens) {
                                for (Player playersTokenToRemove : players) {
                                    table.removeToken(playersTokenToRemove.id, token);
                                }
                                table.removeCard(token);
                            }
                            player.point();
                            updateTimerDisplay(true);
                            //dealerThread.interrupt();
                        }
                        else {
                            // player chose an ilegal set
                            for (int token : tokens) {
                                table.removeToken(player.id, token);
                            }
                            player.penalty();
                        }
                    }
            }


            // // ----------------
            // while(!boardPlayers.isEmpty()){
            //     Player player = null;
            //     try {
            //         player = boardPlayers.take();
            //     }
            //     catch(Exception e) {/*do nothing */}
            //     int[] arr = new int[3];
            //     for (int i = 0; i < 3; i = i + 1){
            //         try{arr[i] = boardSlots.take();} catch(Exception e) {}
            //     }
            //     if (env.util.testSet(arr)) {
            //         for (int i : arr) {
            //             table.removeToken(player.id, i);
            //             table.removeCard(i);
            //         }
            //         player.point();
            //     }
                
                placeCardsOnTable();
            } // finished sync
        }

        // CHECK IF THE SET IS VALID, IF IT IS SO SET 'isValid' to 1, else 0 using setIsValid metod
        // GET THE SLOT AND THE PLAYER FROM 'boardPlayers' 'boardSlots' amd check if boardSlots.length > 2
        // done implement

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        synchronized(dealerLock){
            //hasChanged = true;
            List<Integer> emptySlots = table.getEmptySlots();
            if(emptySlots==null)
                return;
            
            for (int i : emptySlots){
                if (!deck.isEmpty())
                    table.placeCard(deck.remove(0), i);
            }

            // After the dealer has placed the cards, we gonna change back the status to false
            //hasChanged = false;
        }
        // done implement
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
        //hasChanged = true;
        // delete for each player his tokens
        // for (Player player : players) {
        //     player.deleteTokens();
        // }
        // generate random slots
        List<Integer> slots = new LinkedList<>();
        for (int i = 0; i < table.slotToCard.length; i++) {
            slots.add(i);
        }
        //Collections.shuffle(slots);
        // remove cards from table
        for (Integer slot : slots) {
            Integer card = table.slotToCard[slot];
            if (card != null) {
                table.removeCard(slot); 
                deck.add(card);           
            }            
        }
        // shuffle the cards again after removal
        Collections.shuffle(deck);
        // boardSlots.clear();
        // boardPlayers.clear();
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
        // List to array convertion
        int[] winners = new int[maxPlayer.size()];
        for (int i = 0; i < winners.length; i++) {
            winners[i] = maxPlayer.get(i);
        }
        env.ui.announceWinner(winners);
    }

    // public boolean hasChanged() {
    //     return hasChanged;
    // }

    // // Maybe not needed?
    // public void checkPlayerSlots(Player player, int[] slots) {
    //     try {
    //         for (int i = 0; i < slots.length; i++) {
    //             boardSlots.put(slots[i]);
    //         }
    //         boardPlayers.put(player);
    //         dealerThread.interrupt();
    //     } catch (InterruptedException e) {}
    // }

    private boolean areAvailableSets() {
        // creation of list should be done in Table
        LinkedList<Integer> cards = new LinkedList<>();
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null)
                cards.add(table.slotToCard[i]);
        }
        return env.util.findSets(cards, 1).size() != 0;
    }
}
