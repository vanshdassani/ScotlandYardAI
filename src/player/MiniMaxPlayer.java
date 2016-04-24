package player;

import weighter.Weighter;
import aigraph.*;
import prijkstra.*;
import graph.*;
import scotlandyard.*;

import java.io.IOException;
import java.util.*;

/**
 * A class for a player in a Scotland Yard game, implementing Player. A player
 * of this class will choose a move automatically, by creating a game tree, with
 * alpha-beta pruning, and selecting a move based on the MiniMax algorithm. The
 * score for each possible state of the game is based on numerous factors.
 * @see Player the interface for a Player in this game.
 *
 * TODO create game tree
 * TODO alpha-beta pruning
 * TODO make score more complex
 * TODO Dijkstra must not let Detective-Detective journeys have boats.
 */
public class MiniMaxPlayer implements Player {

    private int location;
    private ScotlandYardView currentGameState;
    private HashMap<Colour, Integer> playerLocationMap;
    private DijkstraCalculator dijkstraGraph;
    private List<Move> moves;

    public MiniMaxPlayer(ScotlandYardView view, String graphFilename) {
        // store current game
        this.currentGameState = view;

        // store graph
        ScotlandYardGraphReader graphReader = new ScotlandYardGraphReader();
        try {
            // read the graph, convert it to a DijkstraCalculator and store it.
            Reader reader = new Reader();
            reader.read("lib/scotlandyard.jar/" + graphFilename);
            this.dijkstraGraph = new DijkstraCalculator(reader.graph());
        } catch (IOException e) {
            System.err.println("failed to read " + graphFilename);
        }

        // store locations of other players
        playerLocationMap = new HashMap<>();
        for (Colour player : currentGameState.getPlayers()) {
            playerLocationMap.put(player, currentGameState.getPlayerLocation(player));
        }
    }


    /**
     * Gets the current state of the game from Receiver (the Scotland Yard
     * model), chooses a move, and tells the receiver to play it. This method
     * should be called by the receiver.
     *
     * @param location the location of the player to request a move from.
     * @param moves a list of valid moves the player can make.
     * @param token the token to verify correct player returns a move: it is
     *              given back to the receiver.
     * @param receiver the Receiver who makes this method call.
     */
    @Override
    public void notify(int location, List<Move> moves, Integer token,
                       Receiver receiver) {
        // update current game state
        if (!(receiver instanceof ScotlandYardView))
            throw new IllegalArgumentException("Receiver must implement ScotlandYardView also");
        this.currentGameState = (ScotlandYardView) receiver;
        this.moves = moves;
        this.location = location;

        // get ai move
        System.out.println("Getting move");
        Move aiMove = getAIMove();

        // play ai move
        System.out.println("Playing move: " + aiMove);
        receiver.playMove(aiMove, token);
    }

    /**
     * Chooses a move.
     *
     * @return the chosen move.
     */
    Move getAIMove() {
        // create new game tree to specified depth, with root as current state of the game
        //int treeDepth = 0;
        //ScotlandYardGameTree gameTree = new ScotlandYardGameTree(currentGameState);
        //generateTree(gameTree, treeDepth);//calls pruneTree(), score()
        HashMap<Move, Double> moveScores = score();
        // return best moved based on MiniMax
        //List<AINode<ScotlandYardView>> finalStates = gameTree.getFinalStatesList();
        //return minimax(finalStates);

        // return key associated with highest value
        return Collections.max(moveScores.entrySet(), (entry1, entry2) -> (entry1.getValue() > entry2.getValue()) ? 1 : -1).getKey();
    }

    /**
     * Calculates scores for all given moves.
     *
     * @return a HashMap of each given move to its score.
     */
    private HashMap<Move, Double> score() {
        // create map of moves to scores
        HashMap<Move, Double> moveScoreMap = new HashMap<>();

        // iterate through possible moves, calculating score for each one and
        // adding to moveScoreMap
        for (Move move : moves) {
            double score = 0;

            // TODO make score more complex
            // TODO score a detective move

            // give a MovePass a score of 0
            if (move instanceof MovePass) {
                score = 0;
            }
            // calculate score for player
            if (move instanceof MoveTicket)
                score = scoreMoveTicket((MoveTicket) move);
            else if (move instanceof MoveDouble)
                score = scoreMoveDouble((MoveDouble) move);

            // put entry (move, score) in map
            moveScoreMap.put(move, score);
        }

        return moveScoreMap;
    }

    /**
     * Assigns a score to a possible MoveTicket using currentGameState, and
     * returns that score.
     *
     * @param move the MoveTicket to calculate score for.
     * @return the score for move.
     */
    private double scoreMoveTicket(MoveTicket move) {
        int score = 0;

        // give a move a higher score if it results in MrX being further away
        // from detectives
        for (Colour player : currentGameState.getPlayers()) {
            // no need to calculate distance between player and himself
            if (move.colour == player) continue;

            // calculate shortest route from MiniMax player to other player
            Graph<Integer, Transport> route = dijkstraGraph.getResult(move.target, playerLocationMap.get(player), TRANSPORT_WEIGHTER);

            // add weight of each edge in route to score
            if (move.colour == Colour.Black) {
                // add more to score if edge requires greater value transport
                // to traverse.
                for (Edge<Integer, Transport> e : route.getEdges())
                    score += TRANSPORT_WEIGHTER.toWeight(e.getData());
            }
            else {
                // add more to score if edge requires lesser value transport
                // to traverse. TODO change for route to other detectives vs MrX
                for (Edge<Integer, Transport> e : route.getEdges())
                    score += TRANSPORT_INV_WEIGHTER.toWeight(e.getData());
            }
        }

        return score;
    }

    /**
     * Assigns a score to a possible MoveDouble using currentGameState, and
     * returns that score.
     *
     * @param move the MoveTicket to calculate score for.
     * @return the score for move.
     */
    private double scoreMoveDouble(MoveDouble move) {
        // score the move as if single move, then divide by factor to account
        // for using double move ticket
        return scoreMoveTicket(move.move2) / 2;
    }

    /**
     * An anonymous class that implements Weighter<Transport>, to be passed to
     * Dijkstra's. This Weighter assigns a higher weight to transports with
     * which players start with less tickets for.
     */
    private static final Weighter<Transport> TRANSPORT_WEIGHTER = new Weighter<Transport>() {
        @Override
        public double toWeight(Transport t) {
            int val = 0;
            switch (t) {
                case Taxi:
                    val = 1;
                    break;
                case Bus:
                    val = 2;
                    break;
                case Underground:
                    val = 4;
                    break;
                case Boat:
                    val = 8;
                    break;
            }
            return val;
        }
    };

    /**
     * An anonymous class that implements Weighter<Transport>, to be passed to
     * Dijkstra's. This Weighter assigns a lower weight to transports with
     * which players start with less tickets for.
     */
    private static final Weighter<Transport> TRANSPORT_INV_WEIGHTER = new Weighter<Transport>() {
        @Override
        public double toWeight(Transport t) {
            int val = 0;
            switch (t) {
                case Taxi:
                    val = 8;
                    break;
                case Bus:
                    val = 4;
                    break;
                case Underground:
                    val = 2;
                    break;
                case Boat:
                    val = 0; // detective cannot use a boat.
                    break;
            }
            return val;
        }
    };

    /** TODO generate a tree
     * Generates a game tree to specified depth, given game tree with just root
     * node.
     *
     */
    private void generateTree(ScotlandYardGameTree gameTree, int treeDepth) {

    }

}
