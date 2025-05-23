package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */

// this is a comment

public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    private final HashMap<Integer, List<Integer>> tokens;

    private List<Integer> players;

    private final Object cardsLock;

    private final Object hashLock;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if
     *                   none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if
     *                   none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.tokens = new HashMap<Integer, List<Integer>>();
        this.players = new LinkedList<>();
        this.cardsLock = new Object();
        this.hashLock = new Object();
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the
     * table.
     */
    public void hints() {
        synchronized (cardsLock) {
            List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
            env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
                StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
                List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted()
                        .collect(Collectors.toList());
                int[][] features = env.util.cardsToFeatures(set);
                System.out.println(
                        sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
            });
        }
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        synchronized(cardsLock) {
            int cards = 0;
            for (Integer card : slotToCard)
                if (card != null)
                    ++cards;
            return cards;
        }
    }

    /**
     * Places a card on the table in a grid slot.
     * 
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        synchronized (cardsLock) {
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {
            }

            cardToSlot[card] = slot;
            slotToCard[slot] = card;
            env.ui.placeCard(card, slot); // I can only assume this is the way to use the UI
        }
        // TODO implement
    }

    /**
     * Removes a card from a grid slot on the table.
     * 
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }
        synchronized (cardsLock) {
            if (slotToCard[slot] != null) {
                int card = slotToCard[slot];     
                slotToCard[slot] = null;
                cardToSlot[card] = null;
                synchronized (hashLock) {
                    for (int player : players) {
                        this.removeToken(player, slot);
                    }
                }
                env.ui.removeCard(slot);
            }
            // else slotToCard[slot] == null and there is no card at the slot
        }
        // DONE implement
    }

    /**
     * Places a player token on a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token. 
     *      assumes that there is no token of this playte on this slot
     */
    public void placeToken(int player, int slot) {
        // TODO implement
        synchronized (hashLock) {
            if (!tokens.containsKey(player)) {
                players.add(player);
                tokens.put(player, new LinkedList<Integer>());
            }
            tokens.get(player).add(slot);
            env.ui.placeToken(player, slot);
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // DONE implement
        synchronized (hashLock) {
            if (!tokens.containsKey(player)) {
                return false;
            }

            else {
                if (tokens.get(player).remove((Integer) slot)) {
                    env.ui.removeToken(player, slot);
                    return true;
                }
                return false;
            }
        }
    }

    // returns the slots which are empty 
    public List<Integer> getEmptySlots(){
        synchronized(cardsLock) {
            List<Integer> output = new LinkedList<>();
            for (int i = 0; i < slotToCard.length; i = i+1){
                if (slotToCard[i]==null)
                    output.add(i);
            }
            return output;
        }
    }


    // returns the number of tokens a player has on the table 
    // return 0 if table does not recognize the player
    public int getNumOfTokensOnTable(int player) {
        synchronized(hashLock) {
            if (!tokens.containsKey(player)) 
                return 0;
        
            return tokens.get(player).size();
        }
    }


    // returns an array of cards that the player chose by placing a token on
    // returns an empty array (length = 0) if table does not recognize the player.
    public int[] getTokens(int player) {
        synchronized(cardsLock) {
            synchronized(hashLock){
                if (!tokens.containsKey(player))
                    return new int[0];

                List<Integer> list = new LinkedList<>(tokens.get(player));
                int[] output = new int[list.size()];
                int i = 0;
                for (int token : list) {
                    output[i] = token;
                    i = i + 1;
                }
            
                return output;
            }
        }
    }

    public boolean hasCardAt(int slot) {
        synchronized(cardsLock) {
            return slotToCard[slot] != null;
        }
    }

    public int cardAt(int slot) {
        synchronized(cardsLock) {
            Integer output = slotToCard[slot];
            if (output == null)
                return -1;

            return output;
        }
    }

    public boolean checkAndRemoveSet(int player, Dealer dealer) {
        // returns true if player has a set
        // else returns false
        synchronized(cardsLock){
            synchronized(hashLock) {

                if (!players.contains((Integer) player)) // if player does not exist
                    return false;

                if (this.getNumOfTokensOnTable(player) != dealer.setSize)
                    return false;

                List<Integer> currentTokens = new LinkedList<>(tokens.get(player));
                int[] cards = new int[currentTokens.size()];
                
                // copy currentTokens into cards
                int i = 0;
                for (int token : currentTokens) {
                    if(slotToCard[token] == null)
                        return false;

                    
                    cards[i] = slotToCard[token];
                    i = i + 1;
                }

                if (dealer.testSet(cards)) {
                    // player chose a legal set
                    
                    // remove cards of the set
                    for (int token : currentTokens) {
                        this.removeCard(token);
                    }
                    return true;
                }

                // is not a legal set. return false
                return false;

            }
        }
    }

    public List<Integer> getAllCards() {
        synchronized(cardsLock) {
            List<Integer> output = new LinkedList<>();
            for (int i = 0; i < slotToCard.length; i = i + 1){
                if (slotToCard[i] != null) {
                    output.add(slotToCard[i].intValue());
                }
            }

            return output;
        }
    }

    public List<Integer> removeAllCards() {
        env.logger.info("thread " + Thread.currentThread().getName() + " Table remove all cards");
        synchronized(cardsLock) {
            synchronized(hashLock) {
                List<Integer> output = this.getAllCards();

                for (int i = 0; i < slotToCard.length; i = i + 1) {
                    this.removeCard(i);
                }

                return output;
            }
        }
    }

    public boolean areAvailableSets() {
        synchronized(cardsLock) {
            List<Integer> temp = this.getAllCards();

            return env.util.findSets(temp, 1).size() >= 1;
        }
    }

    public void placeCardsOnTable(List<Integer> deck) {
        synchronized (cardsLock) {
            List<Integer> emptySlots = this.getEmptySlots();
            for (int slot : emptySlots) {
                if(deck.isEmpty())
                    return;
                    
                this.placeCard(deck.remove(0), slot);
            }
        }
    }


}