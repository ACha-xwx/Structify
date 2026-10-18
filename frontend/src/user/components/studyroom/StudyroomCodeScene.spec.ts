import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import StudyroomCodeScene from './StudyroomCodeScene.vue';
const { api } = vi.hoisted(() => ({ api: { listCodeSamples: vi.fn(), runCode: vi.fn() } }));
vi.mock('../../runtime', () => ({ userApi: api }));
beforeEach(() => {
  api.listCodeSamples.mockResolvedValue({ lessons: [{ samples: [{ id: 'code', code: 'int main() {\n return 0;\n}', stdin: '' }] }] });
  api.runCode.mockRejectedValue(new Error('代码运行服务未配置'));
});
describe('code teaching scene', () => {
  it('ignores a late execution result after changing the code resource', async () => {
    let finish: (value: unknown) => void = () => {};
    api.listCodeSamples.mockResolvedValue({ lessons: [{ samples: [{ id: 'code', code: 'old source', stdin: '' }, { id: 'code-2', code: 'new source', stdin: '' }] }] });
    api.runCode.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    const resources = ['code', 'code-2'].map(id => ({ id, type: 'C_CODE', title: id, description: '', parameters: {}, available: true }));
    const wrapper = mount(StudyroomCodeScene, { props: { resources, scene: { id: 'main', kind: 'C_CODE', purpose: '实现', codeMode: 'READ', items: [{ resourceId: 'code', explanation: '' }] } } });
    await flushPromises();
    await wrapper.get('button[aria-label="打开代码实验"]').trigger('click');
    await wrapper.get('button[aria-label="运行 C 程序"]').trigger('click');
    await wrapper.setProps({ scene: { id: 'new', kind: 'C_CODE', purpose: '新示例', codeMode: 'READ', items: [{ resourceId: 'code-2', explanation: '' }] } });
    await flushPromises();
    finish({ status: 'success', stdout: 'stale output', stderr: '' }); await flushPromises();
    expect(wrapper.text()).toContain('new source');
    expect(wrapper.text()).not.toContain('stale output'); wrapper.unmount();
  });
  it('starts read-only and keeps the source readable when execution fails', async () => {
    const wrapper = mount(StudyroomCodeScene, { props: { scene: { id: 'main', kind: 'C_CODE', purpose: '理解实现', codeMode: 'READ', items: [{ resourceId: 'code', explanation: '观察返回值' }] }, resources: [{ id: 'code', type: 'C_CODE', title: '实现', description: '', parameters: {}, available: true }] } });
    await flushPromises();
    expect(wrapper.find('textarea').exists()).toBe(false);
    expect(wrapper.text()).toContain('return 0');
    await wrapper.get('button[aria-label="打开代码实验"]').trigger('click');
    await wrapper.get('button[aria-label="运行 C 程序"]').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('代码运行服务未配置');
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toContain('return 0');
  });
});
