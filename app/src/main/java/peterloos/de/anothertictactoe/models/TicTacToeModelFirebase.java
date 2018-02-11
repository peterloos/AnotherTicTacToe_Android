package peterloos.de.anothertictactoe.models;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import peterloos.de.anothertictactoe.Globals;
import peterloos.de.anothertictactoe.interfaces.ITicTacToe;
import peterloos.de.anothertictactoe.interfaces.OnBoardChangedListener;
import peterloos.de.anothertictactoe.interfaces.OnPlayersConfigurationChangedListener;

import static peterloos.de.anothertictactoe.Globals.Dimension;

/**
 * Created by loospete on 28.01.2018.
 */

class Cell {

    private String state;

    public Cell() {
        // default constructor required for Firebase
    }

    public Cell(String state) {
        this.state = state;
    }

    public String getState() {
        return this.state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "State: ==> " + this.state;
    }
}

// firebase how to create server timestamp java

// https://stackoverflow.com/questions/36658833/firebase-servervalue-timestamp-in-java-data-models-objects

// https://www.programcreek.com/java-api-examples/index.php?api=com.firebase.client.ServerValue

class Player {

    private String name;
    private long creationDate;

    // c'tors
    public Player() {
        // default constructor required for Firebase
    }

    public Player(String name) {
        this.name = name;
    }

    // getter/setter
    public Map<String, String> getCreationDate() {
        return ServerValue.TIMESTAMP;
    }

    @Exclude
    public long getCreationDateLong() {
        return creationDate;
    }

    public void setCreationDate(long creationDate) {
        this.creationDate = creationDate;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Name: " + this.name;
    }
}

public class TicTacToeModelFirebase implements ITicTacToe {

    // Firebase utils
    private FirebaseDatabase database;
    private DatabaseReference refBoard;
    private DatabaseReference refPlayers;

    // general member data
    private Context context;

    // game utils
    private HashMap<String, String> board;
    private GameState gameState;
    private GameStone stone;

    // players utils
    // TODO: Das sollte ein array von Objekten sein !!!!!!!!!!!!!!!1
    private String[] playerNames;
    private String[] playerKeys;
    private long[] playerTimestamps;
    private String currentPlayer;
    private int numPlayers;
    private int[] playerScores;
    private boolean wonLastGame;

    // listeners
    private OnBoardChangedListener boardListener;
    private OnPlayersConfigurationChangedListener playersListener;

    private ValueEventListener valueEventListener;

    // c'tor
    public TicTacToeModelFirebase(Context context) {

        this.context = context;

        this.gameState = GameState.Inactive;
        this.board = new HashMap<>();

        // TODO: Warum steht das nicht in initGame
        this.playerNames = new String[2];
        this.playerKeys = new String[2];
        this.playerNames[0] = "";
        this.playerNames[1] = "";
        this.currentPlayer = "";
        this.playerTimestamps = new long[2];
        this.playerScores = new int[2];
        this.numPlayers = 0;
        this.wonLastGame = false;

        // init access to database
        this.database = FirebaseDatabase.getInstance();
        this.refBoard = database.getReference("board");

        // setup listener object for 'value changed' events
        this.valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.exists()) {

                    TicTacToeModelFirebase.this.evaluateBoardSnapshot(dataSnapshot);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                // TODO: Error handling
                Log.w(Globals.Tag, "Failed to read value.", error.toException());
            }
        };
        this.refBoard.addValueEventListener(this.valueEventListener);

        this.refPlayers = database.getReference("players");
        this.refPlayers.addChildEventListener(this.childEventListener);

        this.startGame();
    }

    // implementation of interface 'ITicTacToe'

    @Override
    public void setOnBoardChangedListener(OnBoardChangedListener listener) {

        this.boardListener = listener;
    }

    @Override
    public void setOnPlayersChangedListener(OnPlayersConfigurationChangedListener listener) {

        this.playersListener = listener;
    }

    @Override
    public void registerPlayer(String name) {

        if (this.currentPlayer.equals("")) {

            this.currentPlayer = name;

            Player player = new Player(name);
            this.refPlayers.push().setValue(player);
        }
    }

    @Override
    public void unregisterPlayer() {

        if (!this.currentPlayer.equals("")) {

            this.refPlayers.child(this.playerKeys[0]).removeValue();
        }
    }

    @Override
    public void startGame() {

        // need at first an cleared hash map
        for (int row = 1; row <= Dimension; row++) {
            for (int col = 1; col <= Dimension; col++) {
                String key = this.cellToKey(row, col);
                this.board.put(key, GameStone.Empty.toString());
            }
        }

        // now initialize remote database
        // GEHT --- HABE ES ABER MIT DEM AUFRUF von clearAllStonesRemote IN EINEM AUFWASCH GEMACHT !!!
//        for (int row = 1; row <= Dimension; row++) {
//            for (int col = 1; col <= Dimension; col++) {
//                this.setSingleStoneRemote(row, col, GameStone.Empty);
//            }
//        }
        this.clearAllStonesRemote();

        if (this.boardListener != null) {
            this.boardListener.clearBoard();
        }
    }

    @Override
    public void restartGame() {

        this.startGame();

        // compute who begins to play
        if (this.wonLastGame) {

            TicTacToeModelFirebase.this.gameState = GameState.ActiveIsOther;
            TicTacToeModelFirebase.this.stone = GameStone.O;
        } else {

            TicTacToeModelFirebase.this.gameState = GameState.ActiveIsMe;
            TicTacToeModelFirebase.this.stone = GameStone.X;
        }

        String info = "";
        if (this.gameState == GameState.ActiveIsMe) {
            info = "Player " + this.playerNames[1] + " begins !!!";
        } else if (this.gameState == GameState.ActiveIsOther) {
            info = "Player " + this.playerNames[0] + " begins !!!";
        }
        Toast.makeText(this.context, info, Toast.LENGTH_SHORT).show();

        // fire notification according to players state
        if (this.playersListener != null) {

            if (this.wonLastGame) {

                this.playersListener.playersActivityStateChanged(false, true);
            }
            else {

                this.playersListener.playersActivityStateChanged(true, false);
            }
        }
    }

    private ChildEventListener childEventListener = new ChildEventListener()
    {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {

            // a new player has been added
            Player player = dataSnapshot.getValue(Player.class);
            String name = player.getName();

            TicTacToeModelFirebase.this.numPlayers++;

            Log.d(Globals.Tag, "onChildAdded: " + player.toString() + " [" + dataSnapshot.getKey() + "]");

            if (TicTacToeModelFirebase.this.currentPlayer.equals(name)) {

                TicTacToeModelFirebase.this.playerNames[0] = name;
                TicTacToeModelFirebase.this.playerKeys[0] = dataSnapshot.getKey();
                TicTacToeModelFirebase.this.playerTimestamps[0] = player.getCreationDateLong();
            } else {

                TicTacToeModelFirebase.this.playerKeys[1] = dataSnapshot.getKey();
                TicTacToeModelFirebase.this.playerNames[1] = name;
                TicTacToeModelFirebase.this.playerTimestamps[1] = player.getCreationDateLong();
            }

            // compute who begins to play
            if (TicTacToeModelFirebase.this.numPlayers == 2) {

                if (TicTacToeModelFirebase.this.playerTimestamps[0] < TicTacToeModelFirebase.this.playerTimestamps[1]) {

                    TicTacToeModelFirebase.this.gameState = GameState.ActiveIsMe;
                    TicTacToeModelFirebase.this.stone = GameStone.X;
                } else {

                    TicTacToeModelFirebase.this.gameState = GameState.ActiveIsOther;
                    TicTacToeModelFirebase.this.stone = GameStone.O;
                }
            }

            // fire notification
            if (TicTacToeModelFirebase.this.playersListener != null) {

                TicTacToeModelFirebase.this.playersListener.playersNamesChanged
                        (TicTacToeModelFirebase.this.playerNames[0],
                         TicTacToeModelFirebase.this.playerNames[1]);
            }
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {
        }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {

            // removing a player
            Player player = dataSnapshot.getValue(Player.class);
            String name = player.getName();

            TicTacToeModelFirebase.this.numPlayers--;

            Log.d(Globals.Tag, "onChildRemoved: " + name + " [" + dataSnapshot.getKey() + "]");

            // update internal data structures
            if (TicTacToeModelFirebase.this.currentPlayer.equals(name)) {

                // this (current) player is removed
                TicTacToeModelFirebase.this.currentPlayer = "";
                TicTacToeModelFirebase.this.playerNames[0] = "";
            } else {
                // second player is removed
                TicTacToeModelFirebase.this.playerNames[1] = "";
            }

            // fire notification
            if (TicTacToeModelFirebase.this.playersListener != null) {

                TicTacToeModelFirebase.this.playersListener.playersNamesChanged
                        (TicTacToeModelFirebase.this.playerNames[0],
                                TicTacToeModelFirebase.this.playerNames[1]);
            }
        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) {
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

            Log.w(Globals.Tag, "refPlayers:onCancelled", databaseError.toException());
            Toast.makeText(TicTacToeModelFirebase.this.context, "Failed to load comments.",
                    Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    public GameStone getStoneAt(int row, int col) {

        String key = this.cellToKey(row, col);
        String value = this.board.get(key);
        return GameStone.valueOf(value);
    }

    @Override
    public boolean setStone(int row, int col) {

        String key = this.cellToKey(row, col);

        // ignore this request - current game over or not initialized
        if (this.gameState == GameState.Inactive)
            return false;

        // it's not your turn
        if (this.gameState == GameState.ActiveIsOther) {

            String msg = String.format("It's %s's turn!", this.playerNames[1]);
            Toast.makeText(this.context, msg, Toast.LENGTH_SHORT).show();
            return false;
        }

        // is there already a stone, ignore call
        if (!isFieldEmpty(key))
            return false;

        // set stone on board
        Log.v("PeLo", "setStone ==> row = " + row + ", col = " + col);
        this.setSingleStoneRemote(row, col, stone);

        return true;
    }

    // private helper methods
    private void evaluateBoardSnapshot(DataSnapshot dataSnapshot) {

        for (DataSnapshot data : dataSnapshot.getChildren()) {

            Log.d(Globals.Tag, "    Key:   " + data.getKey());
            for (DataSnapshot subData : data.getChildren()) {

                if (subData.getKey().equals("col1")) {

                    Cell cell = subData.getValue(Cell.class);
                    this.onCellChanged(data.getKey(), "col1", cell.getState());
                } else if (subData.getKey().equals("col2")) {

                    Cell cell = subData.getValue(Cell.class);
                    this.onCellChanged(data.getKey(), "col2", cell.getState());
                } else if (subData.getKey().equals("col3")) {

                    Cell cell = subData.getValue(Cell.class);
                    this.onCellChanged(data.getKey(), "col3", cell.getState());
                }
            }
        }
    }

    private void onCellChanged(String row, String col, String stone) {

        String msg = String.format("bin hier in onCellChanged: %s  %s mit Stein %s", row, col, stone);
        Log.v(Globals.Tag, msg);

        String key = this.cellToKey(row, col);
        String value = this.board.get(key);

        GameStone oldStone = GameStone.valueOf(value);
        GameStone newStone = GameStone.valueOf(stone);

        if (oldStone != newStone) {

            // enter new stone into hash map of model
            this.board.put(key, stone);

            // fire notification according to state of board
            if (this.boardListener != null) {

                this.boardListener.stoneChangedAt(
                        this.rowToInt(row),
                        this.colToInt(col),
                        GameStone.valueOf(stone));
            }

            // check for end of game
            if (this.checkForEndOfGame(newStone)) {

                String result = "";
                if (this.gameState == GameState.ActiveIsMe) {

                    result = "Congratulations - " + this.playerNames[0] + "\n"+ "You've won !!!";

                    this.wonLastGame = true;

                } else if (this.gameState == GameState.ActiveIsOther) {

                    result = "Sorry - " + this.playerNames[0] + "\n"+ "You just lost the game !!!";

                    this.wonLastGame = false;
                }
                Toast.makeText(this.context, result, Toast.LENGTH_SHORT).show();

                this.gameState = GameState.Inactive;

                return;
            }

            // switch players state
            if (this.gameState == GameState.ActiveIsMe) {

                this.gameState = GameState.ActiveIsOther;
            } else if (this.gameState == GameState.ActiveIsOther) {

                this.gameState = GameState.ActiveIsMe;
            }

            // fire notification according to players state
            if (this.playersListener != null) {

                this.playersListener.playersActivityStateChanged(
                        (this.gameState == GameState.ActiveIsMe) ? true : false,
                        (this.gameState == GameState.ActiveIsMe) ? false : true);
            }
        }
    }

    private String cellToKey(int row, int col) {

        // assertion: row and col are in the range 1..3
        return Integer.toString((row - 1) * Dimension + col);
    }

    private String cellToKey(String srow, String scol) {

        int row = this.rowToInt(srow);
        int col = this.colToInt(scol);
        return this.cellToKey(row, col);
    }

    private boolean isFieldEmpty(String key) {

        String value = this.board.get(key);
        return value.equals(GameStone.Empty.toString());
    }

    private int rowToInt(String row) {

        return row.charAt(3) - '0';
    }

    private int colToInt(String row) {

        return row.charAt(3) - '0';
    }

    private void setSingleStoneRemote(int r, int c, GameStone stone) {

        String row = "row" + r;
        String col = "col" + c;
        Cell cell = new Cell(stone.toString());
        this.refBoard.child(row).child(col).setValue(cell);
    }

    private void clearAllStonesRemote() {

        Board board = new Board();

        Col col = new Col ();
        col.setState("Empty");

        Row row = new Row();
        row.setCol1(col);
        row.setCol2(col);
        row.setCol3(col);

        board.setRow1(row);
        board.setRow2(row);
        board.setRow3(row);

        this.refBoard.setValue (board);
    }

    private boolean checkForEndOfGame(GameStone stone) {

        /*
         * check for end of game, using a hash map based board :-)
         */

        // test columns
        for (int row = 1; row <= 3; row++) {

            GameStone stone1 = this.getStoneAt(row, 1);
            GameStone stone2 = this.getStoneAt(row, 2);
            GameStone stone3 = this.getStoneAt(row, 3);

            if (stone1 == stone && stone2 == stone && stone3 == stone)
                return true;
        }

        // test rows
        for (int col = 1; col <= 3; col++) {

            GameStone stone1 = this.getStoneAt(1, col);
            GameStone stone2 = this.getStoneAt(2, col);
            GameStone stone3 = this.getStoneAt(3, col);

            if (stone1 == stone && stone2 == stone && stone3 == stone)
                return true;
        }

        // test diagonals
        GameStone stone11 = this.getStoneAt(1, 1);
        GameStone stone22 = this.getStoneAt(2, 2);
        GameStone stone33 = this.getStoneAt(3, 3);

        if (stone11 == stone && stone22 == stone && stone33 == stone)
            return true;

        GameStone stone31 = this.getStoneAt(3, 1);
        GameStone stone13 = this.getStoneAt(1, 3);

        if (stone31 == stone && stone22 == stone && stone13 == stone)
            return true;

        // could be a draw - count empty stones
        int emptyStones = 0;
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 3; col++) {
                GameStone s = this.getStoneAt(row, col);
                if (s == GameStone.Empty) {
                    emptyStones++;
                    break;
                }
            }
        }

        if (emptyStones == 0) {

            this.gameState = GameState.Inactive;
            Toast.makeText(this.context, "Game over - It's a draw!", Toast.LENGTH_SHORT).show();

            // fire notification according to players state
            if (this.playersListener != null) {

                this.playersListener.playersActivityStateChanged(false, false);
            }
        }

        return false;
    }


    public static class Board {

        private Row row1;
        private Row row2;
        private Row row3;

        public Row getRow1() {
            return row1;
        }

        public void setRow1(Row row1) {
            this.row1 = row1;
        }

        public Row getRow2() {
            return row2;
        }

        public void setRow2(Row row2) {
            this.row2 = row2;
        }

        public Row getRow3() {
            return row3;
        }

        public void setRow3(Row row3) {
            this.row3 = row3;
        }
    }

    public static class Row {

        private Col col1;
        private Col col2;
        private Col col3;

        public Col getCol1() {
            return col1;
        }

        public void setCol1(Col col1) {
            this.col1 = col1;
        }

        public Col getCol2() {
            return col2;
        }

        public void setCol2(Col col2) {
            this.col2 = col2;
        }

        public Col getCol3() {
            return col3;
        }

        public void setCol3(Col col3) {
            this.col3 = col3;
        }
    }

    public static class Col {

        private String state;

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }


}
