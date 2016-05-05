package player;

import aigraph.AINode;
import aigraph.ScotlandYardGameTree;
import graph.Edge;
import graph.Graph;
import prijkstra.DijkstraCalculator;
import prijkstra.Weighter;
import scotlandyard.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A class for a player in a Scotland Yard game, implementing Player. A player
 * of this class will choose a move automatically, by creating a game tree, with
 * alpha-beta pruning, and selecting a move based on the MiniMax algorithm. The
 * score for each possible state of the game is based on numerous factors.
 * @see Player the interface for a Player in this game.
 */
public class MiniMaxPlayer implements Player {

    private int location; // not used anymore
    private final Colour colour;
    private final ScotlandYardView view;
    private ScotlandYardState currentGameState;
    private Map<Colour, Integer> playerLocationMap; // not used anymore
    private ScotlandYardGraph graph;
    private ScotlandYardGameTree gameTree;
    private final DijkstraCalculator dijkstraGraph;
    private List<Move> moves;


    @SuppressWarnings("unchecked")
    public MiniMaxPlayer(ScotlandYardView view, String graphFilename, Colour colour) {
        // store graph
        ScotlandYardGraphReader graphReader = new ScotlandYardGraphReader();
        try {
            // read the graph, convert it to a DijkstraCalculator and store it.
            this.graph = graphReader.readGraph(graphFilename);
        } catch (IOException e) {
            System.err.println("failed to read " + graphFilename);
            e.printStackTrace();
        }

        // store player colour
        this.colour = colour;

        // store current game
        this.view = view;

        // store dijkstra graph
        //noinspection unchecked
        this.dijkstraGraph = new DijkstraCalculator(this.graph);
    }


    /**
     * Gets the current state of the game from a Receiver (the Scotland Yard
     * model), chooses a move, and makes the receiver play it. This method
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

        // get data for fields
        this.moves = moves;
        this.location = location;

        // create a ScotlandYardState based on view and location
        this.currentGameState = new ScotlandYardState(view, location, graph);

        // store locations of other players
        playerLocationMap = currentGameState.getPlayerLocations();

        // get ai move
        Move aiMove = getAIMove();

        // play ai move
        receiver.playMove(aiMove, token);
    }


    /**
     * Chooses a move.
     *
     * @return the chosen move.
     */
    private Move getAIMove() {
        // initialise tree with the current game state at root node
        gameTree = new ScotlandYardGameTree(currentGameState);

        // calculate a score for each move by using the MiniMax algorithm.
        // return the move with the best score.
        int depth = 12;
        boolean mrx = colour.equals(Colour.Black);
        return score(depth, mrx);
    }


    /**
     * Calculates scores for all given moves.
     *
     * @return the move which results in the game state with the highest score
     *         after looking at every possibility after {@code depth} moves are
     *         played.
     */
    private Move score(int depth, boolean mrx) {
        // initialise move ai will choose
        Move aiMove = moves.get(0);

        // generate the game tree
        generateTree(gameTree, depth, mrx);

        // choose the move
        double bestMoveScore = gameTree.getHead().getScore();

        // check all first level edges to see which move gave the best score
        for (Edge<ScotlandYardState, Move> possibleBest : gameTree.getListFirstLevelEdges()) {
            if (((AINode) possibleBest.getTarget()).getScore() == bestMoveScore) { // ERROR IF STATEMENT BROKEN
                aiMove = possibleBest.getData();
                break;
            }
        }
//        if (!gotAMove) System.out.println("score(): Move was not assigned " +
//                "from gameTree");
//        else System.out.println("score(): Move was assigned from gameTree");

        return aiMove;
    }


    /**
     * Generates a gameTree given the head node, which should have have
     * {@code gameTree.head} as an AINode whose index field should be a
     * ScotlandYardState object holding the current state of the game.
     *
     * @param gameTree the graph containing just the head node of the MiniMax
     *                 tree
     * @param depth depth to which to generate the tree
     * @param max true if this player wants to maximise the score
     */
    private void generateTree(ScotlandYardGameTree gameTree, int depth, boolean max) {

        // generate the tree
        MiniMax(gameTree.getHead(), depth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, max);

        // set the score for game tree head
        gameTree.getHead().setScore(Collections.max(gameTree.getFirstLevelScores()));
    }


    /**
     * Implements the MiniMax algorithm with alpha-beta pruning.
     *
     * @param node the AINode from which to create a tree from. node will be
     *             head of the (sub)tree.
     * @param depth the number of lays to generate for the tree.
     * @param alpha should be set to initial value Double.NEGATIVE_INFINITY.
     * @param beta should be set to initial value Double.POSITIVE_INFINITY.
     * @param max If true, the player who's turn it is in
     *            {@code node.getGameState()}
     *            should be a maximising player, else minimising player.
     * @return the best possible score that scoreMoveTicket or
     *         scoreMoveDouble assigns that
     *         {@code node.getGameState().getCurrentPlayer()}
     *         can get, based on a tree of {@code depth} depth.
     */
    private Double MiniMax(AINode node, int depth, double alpha, double beta, boolean max) {
        // base case 1 - depth is zero
        if (depth <= 0) {
            calculateScore(node);
            return node.getScore();
        }

        // create children nodes and add to tree
        //System.out.println("number of valid moves: " + node.getGameState().validMoves(node.getGameState().getCurrentPlayer()).size());
//        System.out.println(node.getGameState().getCurrentPlayer());

        for (Move move : node.getGameState().validMoves(node.getGameState().getCurrentPlayer())) {
            // make a copy of currentGameState for the next move
            ScotlandYardState stateAfterMove = node.getGameState().copy();
            stateAfterMove.playMove(move);

            // create node for this game state and link to tree.
            // Give unassigned score = 0
            AINode child = new AINode(stateAfterMove, 0.0);
            gameTree.add(child);
            Edge<ScotlandYardState, Move> edgeToChild = new Edge<>(node, child, move);
            gameTree.add(edgeToChild);
            calculateScore(child);
        }

        // base case 2 - node is leaf (no valid moves)
        if (gameTree.getChildren(node).size() == 0) {
            calculateScore(node);
            return node.getScore();
        }

        // store the score for the best move
        Double v;

        // this player is maximising MrX score
        if (max) {
            // find child node with highest score
            v = Double.NEGATIVE_INFINITY;

            //System.out.println("MiniMax: NUMBER OF CHILDREN NODES: " + gameTree.getChildren(node).size());
            for (AINode child : gameTree.getChildrenQueue(node, AINODE_COMPARATOR)) {
                // see if next player is maximising or minimising
                boolean isMax = child.getGameState().getCurrentPlayer().equals(Colour.Black);

                v = Math.max(v, MiniMax(child, depth - 1, alpha, beta, isMax));
                alpha = Math.max(alpha, v);
                if (beta <= alpha)
                    break; // beta cut off
            }
        }

        // or player is minimising MrX score
        else {
            // find child with lowest score
            v = Double.POSITIVE_INFINITY;

            //System.out.println("MiniMax: NUMBER OF CHILDREN NODES: " + gameTree.getChildren(node).size());
            for (AINode child : gameTree.getChildrenQueue(node, AINODE_INV_COMPARATOR)) {
                // see if next player is maximising or minimising
                boolean isMax = child.getGameState().getCurrentPlayer().equals(Colour.Black);

                v = Math.min(v, MiniMax(child, depth - 1, alpha, beta, isMax));
                beta = Math.min(beta, v);
                if (beta <= alpha)
                    break; // alpha cut off
            }
        }

        // return the best score
        return v;
    }


    /**
     * Calculates a score for a given node's game state, and sets said node's
     * score.
     *
     * @param node the node to calculate the score for.
     */
    private void calculateScore(AINode node) {
        // init score
        Double score = 0.0;

        // get move, which should be on edge to node. There should only be one
        // edge to node.
        Move moveToNode = gameTree.getEdgesTo(node).get(0).getData();

        // if MrX doesn't lose in this state, calculate score, else, leave the
        // score set to zero
        if (!(!node.getGameState().getWinningPlayers().contains(Colour.Black)
                      && node.getGameState().isGameOver())) {

            // get this node's game state
            ScotlandYardState gameState = node.getGameState();

            // use Dijkstra's and Weighter to assign a score based on distance
            // MrX is from each detective
            score += scoreDistancesState(moveToNode, gameState);

            // adjust score based on degree of node MrX is at
            score += scoreNodeDegree(gameState);

            // adjust score based on factors related to last move played.
            // these should only affect score if MrX played moveToNode
            score += scoreMove(moveToNode, gameState);
        }
        else
            score = Double.NEGATIVE_INFINITY;

        // set node.score to score
//		System.out.println("Move: " + moveToNode + ", Score: " + score);
        node.setScore(score);
    }


    /**
     * Returns a score for a state, based on how far away MrX is from each
     * detective.
     *
     * @param state the state to score.
     * @return the calculated score for the state, based only on how far MrX is
     *         from the detectives.
     */
    private Double scoreDistancesState(Move move, ScotlandYardState state) {
        Double score = 0.0;

        // if a detective is ai player, do not try to find distance to MrX,
        // until MrX has shown himself for the first time.
        if (state.getPlayerLocations().get(Colour.Black) == 0 &&
                    colour != Colour.Black) {
            System.out.println("scoreDistancesState: mrx has not yet revealed: returning 0");
            return 0.0;
        }


		Ticket tick = null;
		boolean tickSet = false;
		if (move instanceof MoveTicket) {
			tick = ((MoveTicket) move).ticket;
			tickSet = true;
		}

        // if a detective is ai player, do not try to find distance to MrX,
        // until MrX has shown himself for the first time.
        if (state.getPlayerLocations().get(Colour.Black) == 0 &&
                    colour != Colour.Black) {
            System.out.println("scoreDistancesState: mrx has not yet revealed: returning 0");
            return 0.0;
        }

        for (Colour detective : state.getPlayers()) {
            // don't find distance between MrX and himself
            if (detective == Colour.Black) continue;

            // calculate shortest route between detective and MrX
            @SuppressWarnings("unchecked")
            Graph<Integer, Transport> route =
                    dijkstraGraph.getResult(state.getPlayerLocations().get(detective), state.getPlayerLocations().get(Colour.Black), TRANSPORT_WEIGHTER);

            // add weight of each edge in route to score.
            // add more to score if edge requires greater value transport
            // to traverse.
            for (Edge<Integer, Transport> e : route.getEdges())
                score += TRANSPORT_WEIGHTER.toWeight(e.getData());

            // if the route is small, decrease score regardless of transport
            // weightings.
            if (route.getEdges().size() < 3) {
                score += -20;
				if (tickSet && tick == Ticket.Secret)
					score += 10;
            }
			else if (route.getEdges().size() > 2) {
				score += 10;
			}

        }
        return score;
    }


    /**
     * Generates and returns a score for a game state based on the degree of the
     * node MrX is at.
     *
     * @return the score for MrX based on his node's degree.
     */
    private double scoreNodeDegree(ScotlandYardState state) {
        Double score = 0.0;
        int degree = 0;

        // get MrX's location
        int mrxLocation = state.getPlayerLocations().get(Colour.Black);

        // get degree of this node
        degree = graph.getUniqueDegree(graph.getNode(mrxLocation));

		// assign a score based on this degree
        if (degree < 7)
            score += -20;
        else if (degree > 6)
            score += 2 * degree;

        return score;
    }


    /**
     * Assigns a score to a possible move using currentGameState, and returns
     * that score.
     *
     * @param move the Move to calculate score for.
     * @return the score for move.
     */
    private double scoreMove(Move move, ScotlandYardState state) {
		if (move instanceof MoveDouble)
            return scoreMoveDouble((MoveDouble) move, state);
        else //MovePass and MoveTicket
            return 0;
    }

    /**
     * Assigns a score to a possible MoveDouble using currentGameState, and
     * returns that score.
     *
     * @param move the MoveTicket to calculate score for.
     * @return the score for move.
     */
    private double scoreMoveDouble(MoveDouble move, ScotlandYardState state) {
        // score the move as if single move, then divide by some factor to
        // account for using a valuable double move ticket
        double score = 0;

        int round = currentGameState.getRound();

        // if next round MrX shows, increase score as double move preferred.
        // increase even more if move.move2 uses secret ticket
        if (currentGameState.getRound() != 0 && currentGameState.getRounds().get(round+1)) {
            score += 20;
            if (move.move2.ticket == Ticket.Secret)
                score += 20; // double move when having to show even better
                             // when move2 is secret
        }
		else
			score += -50; // else, double move not preferred


        return score;
    }


    // Comparator and Weighter objects

    /**
     * A comparator for AINode that ranks AINodes with a greater score field as
     * higher.
     */
    private static final Comparator<AINode> AINODE_COMPARATOR =
           (node1, node2) -> node1.getScore().intValue() - node2.getScore().intValue();


    /**
     * A comparator for AINode that ranks AINodes with a smaller score field as
     * higher.
     */
    private static final Comparator<AINode> AINODE_INV_COMPARATOR =
           (node1, node2) -> node2.getScore().intValue() - node1.getScore().intValue();


    /**
     * A lambda function implements Weighter<Transport>. This Weighter assigns a lower
     * weight to transports with which players can move further with.
     */
    private static final Weighter<Transport> TRANSPORT_WEIGHTER = t -> {
        int val = 0;
        switch (t) {
            case Taxi:
                val = 1;
                break;
            case Bus:
                val = 2;
                break;
            case Underground:
                val = 2;
                break;
            case Boat:
                val = 20; // Really high as detective can't use a boat
                break;
        }
        return val;
    };
}
