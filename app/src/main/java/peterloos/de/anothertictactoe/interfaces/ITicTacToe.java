package peterloos.de.anothertictactoe.interfaces;

import peterloos.de.anothertictactoe.models.GameStone;

/**
 * Created by loospete on 27.01.2018.
 */

public interface ITicTacToe {

    void setOnBoardChangedListener (OnBoardChangedListener listener);

    void initGame();
    GameStone getStoneAt (int row, int col);
    boolean setStone(int row, int col);
}