import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { inspectionTodoCount } from './summary.js';

describe('personal inbox inspection summary', () => {
  it('counts electric-box execution and review todos', () => {
    assert.equal(inspectionTodoCount({ INSPECTION: 2, REVIEW: 3 }), 5);
  });

  it('counts edge inspection execution, rectification and review todos', () => {
    assert.equal(inspectionTodoCount({
      EDGE_INSPECTION_TASK: 3,
      EDGE_INSPECTION_RECTIFICATION: 2,
      EDGE_INSPECTION_REVIEW: 1,
    }), 6);
  });

  it('includes edge assignment todos in the inspection total', () => {
    assert.equal(inspectionTodoCount({
      EDGE_INSPECTION_TASK_ASSIGN: 2,
      EDGE_INSPECTION_RECTIFICATION_ASSIGN: 1,
    }), 3);
  });

  it('combines both inspection areas and ignores unrelated or invalid counters', () => {
    assert.equal(inspectionTodoCount({
      INSPECTION: '1',
      REVIEW: 2,
      EDGE_INSPECTION_TASK: '3',
      EDGE_INSPECTION_RECTIFICATION: 4,
      EDGE_INSPECTION_REVIEW: 5,
      SEAL_APPROVAL: 99,
      DOCUMENT_RECEIPT: 99,
      EDGE_INSPECTION_TASK_ASSIGN: 'not-a-number',
      EDGE_INSPECTION_RECTIFICATION_ASSIGN: -2,
    }), 15);
    assert.equal(inspectionTodoCount(), 0);
  });
});
