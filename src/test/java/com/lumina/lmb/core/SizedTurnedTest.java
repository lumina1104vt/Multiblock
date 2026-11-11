package com.lumina.lmb.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A tank's setup (sizes 3..9, four turns): a 5-cube in each turn forms. */
class SizedTurnedTest {
    @Test
    void fiveCubeFormsInEveryTurn() {
        for (Transform turn : Transform.YAW) {
            EngineTest.World w = new EngineTest.World();
            Definition<Character> d = Definition.builder("test:demo",
                    Shape.of(List.of(new Shape.Param("size", 3, 9, 2)), v -> EngineTest.cube(v[0], EngineTest.SHELL))).build();
            w.defs.put(d.id(), d);
            RoleField<Character> f = EngineTest.cube(5, EngineTest.SHELL);
            long anchor = Pos.pack(100, 64, 100);
            for (int i = 0; i < f.volume(); i++) {
                Role<Character> r = f.role(i);
                int lx = f.x(i) - f.anchorX(), ly = f.y(i) - f.anchorY(), lz = f.z(i) - f.anchorZ();
                long pos = Pos.pack(100 + turn.x(lx, ly, lz), 64 + turn.y(lx, ly, lz), 100 + turn.z(lx, ly, lz));
                w.set(pos, r == EngineTest.CONTROLLER ? 'C' : r == EngineTest.INSIDE ? '.' : '#');
                w.tick();
            }
            Instance<Character> s = w.engine.at(anchor);
            assertEquals(Instance.State.FORMED, s.state(), "turn " + turn);
            assertArrayEquals(new int[]{5}, s.params());
            assertEquals(turn, s.transform());
        }
    }
}
