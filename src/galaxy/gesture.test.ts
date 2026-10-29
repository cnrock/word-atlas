import assert from "node:assert/strict";
import test from "node:test";
import { isTap, TAP_SLOP } from "./gesture";

test("finger jitter within touch slop remains a tap", () => {
  assert.equal(isTap({ x: 100, y: 100 }, { x: 108, y: 109 }, false), true);
  assert.equal(isTap({ x: 100, y: 100 }, { x: 100 + TAP_SLOP, y: 100 }, false), true);
});

test("dragging and multi-touch never open a word", () => {
  assert.equal(isTap({ x: 100, y: 100 }, { x: 120, y: 100 }, false), false);
  assert.equal(isTap({ x: 100, y: 100 }, { x: 100, y: 100 }, true), false);
});
