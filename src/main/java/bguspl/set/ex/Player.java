package bguspl.set.ex;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

     /**
     * Dealer entity
     */
    private final Dealer dealer;
    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The key presses of a player
     */
    private ArrayBlockingQueue<Integer> actionsQueue;

    private boolean wasPenalized;

    //private final int setSize;
    
    // /**
    //  * Player's tokes
    //  */
    // private int[] playerTokens; // almog added this field, for blocking queue


    /**
     * Indicator for penalty or point
     */
    //private int isValid;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.actionsQueue = new ArrayBlockingQueue<Integer>(dealer.setSize);
        this.wasPenalized = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) {createArtificialIntelligence();}

        synchronized(this){
            env.logger.info("thread " + Thread.currentThread().getName() + "is locking player + " + this.id);
            while (!terminate) {
                  
                int slot = -1;
                try {
                    slot = actionsQueue.take();
                }
                catch (InterruptedException e) {}
                if (slot >= 0 && !table.removeToken(this.id, slot) && table.hasCardAt(slot) 
                        && table.getNumOfTokensOnTable(this.id) < dealer.setSize) {
                    table.placeToken(this.id, slot);
                    if (table.getNumOfTokensOnTable(this.id)==dealer.setSize && table.checkAndRemoveSet(this.id)) {
                        this.point();
                        dealer.resetTimer();
                    }
                    else if (table.getNumOfTokensOnTable(this.id) == dealer.setSize){
                        this.penalty();
                    }   
                }
            
            }
            //this.notifyAll();
            env.logger.info("thread " + Thread.currentThread().getName() + "is releasing player + " + this.id);
            // end sync
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            Random rnd = new Random();
            while (!terminate) {
                int pressing = rnd.nextInt(env.config.tableSize);
                keyPressed(pressing);
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // try to clear and put into actions queue
        // so blocked threads can continue and then terminate
        try {
            actionsQueue.clear();
            actionsQueue.put(0);
        }
        catch (Exception e) {}
        terminate = true;
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * @pre- actionsQueue needs to be not full
     */
    public void keyPressed(int slot) {
        try {                
            env.logger.info("thread " + Thread.currentThread().getName() + " inserting into actions queue");
            actionsQueue.put(slot);
        } catch (InterruptedException e) {
            //e.printStackTrace();
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        synchronized(this) {
            int ignored = table.countCards(); // this part is just for demonstration in the unit tests
            env.logger.info("thread " + Thread.currentThread().getName() + " Player " + id + "is being point");
            env.ui.setScore(id, ++score);
            try {
                int freezeTime = 1000;
                for (long i = env.config.pointFreezeMillis; i > 0; i -= 1000) {
                    env.ui.setFreeze(id, i);
                    playerThread.sleep(freezeTime);
                }
                env.ui.setFreeze(id, 0);
            } catch (InterruptedException e) {}
            //actionsQueue.clear();
            env.logger.info("thread " + Thread.currentThread().getName() + " Player " + id + "is done being point");
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        synchronized(this) {
            try {
                if (!wasPenalized) {
                    env.logger.info("thread " + Thread.currentThread().getName() + " Player " + id + "is being penalized");
                    long freezeTime = 1000;
                    for (long i = env.config.penaltyFreezeMillis; i > 0; i -= 1000) {
                        env.ui.setFreeze(id, i);
                        playerThread.sleep(freezeTime);
                    }
                    env.ui.setFreeze(id, 0);
                }
            }

            catch (InterruptedException e) {}
            //actionsQueue.clear();
            notifyAll();
            env.logger.info("thread " + Thread.currentThread().getName() + " Player " + id + "is done being penalized");
        }
    }

    public int score() {
        synchronized(this) {
            return score;
        }
    }

}
