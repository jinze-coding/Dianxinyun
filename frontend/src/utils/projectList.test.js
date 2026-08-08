import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { requireProjectList } from './projectList.js';

describe('requireProjectList', () => {
  it('returns a successful project list including an empty list', () => {
    const projects = [{ id: 1, name: '演示项目' }];

    assert.equal(requireProjectList({ code: 200, data: projects }), projects);
    assert.deepEqual(requireProjectList({ code: 200, data: [] }), []);
  });

  it('rejects a non-success business response', () => {
    assert.throws(
      () => requireProjectList({ code: 403, message: '无权访问' }),
      /无权访问/,
    );
  });

  it('rejects a malformed data payload', () => {
    assert.throws(
      () => requireProjectList({ code: 200, data: null }),
      /项目列表接口返回格式不正确/,
    );
  });
});
