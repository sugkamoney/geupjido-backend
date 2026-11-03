package com.geupjido.match.service;

import com.geupjido.match.model.WorldcupGameState;

public record WorldcupGameStartResult(WorldcupGameState state, boolean created) {
}
