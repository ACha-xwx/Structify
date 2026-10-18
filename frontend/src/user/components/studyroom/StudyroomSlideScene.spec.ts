import { mount } from '@vue/test-utils';
import { describe, it, expect, vi } from 'vitest';
import StudyroomSlideScene from './StudyroomSlideScene.vue';
vi.mock('../../runtime', () => ({ userApi: { coursewareImageUrl: (id: string) => `/slides/${id}` } }));
describe('step slide collection', () => {
  it('changes page and explanation together and supports zoom reset', async () => {
    const wrapper = mount(StudyroomSlideScene, { props: {
      scene: { id: 'main', kind: 'PPT', purpose: '观察遍历', items: [{ resourceId: 'p1', explanation: '先看规则' }, { resourceId: 'p2', explanation: '再看例子' }] },
      resources: ['p1', 'p2'].map((id, i) => ({ id, type: 'PPT', title: id, description: `原始页码=${i + 3}`, available: true, parameters: {} })),
    } });
    expect(wrapper.text()).toContain('先看规则');
    await wrapper.get('button[aria-label="下一页"]').trigger('click');
    expect(wrapper.get('img.studyroom__slide').attributes('src')).toBe('/slides/p2');
    expect(wrapper.text()).toContain('再看例子');
    expect(wrapper.text()).toContain('2 / 2');
    await wrapper.get('button[aria-label="放大"]').trigger('click');
    expect(wrapper.text()).toContain('125%');
    await wrapper.get('button[aria-label="恢复整页"]').trigger('click');
    expect(wrapper.text()).toContain('100%');
  });
  it('retries a failed page without substituting a different resource', async () => {
    const wrapper = mount(StudyroomSlideScene, { props: { scene: { id: 'p', kind: 'PPT', purpose: '看图', items: [{ resourceId: 'p1', explanation: '' }] }, resources: [{ id: 'p1', type: 'PPT', title: '图', description: '', parameters: {}, available: true }] } });
    await wrapper.get('img.studyroom__slide').trigger('error');
    expect(wrapper.text()).toContain('加载失败');
    await wrapper.get('button[aria-label="重试当前页"]').trigger('click');
    expect(wrapper.find('img.studyroom__slide').exists()).toBe(true);
  });
});
