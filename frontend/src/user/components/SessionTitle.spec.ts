import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import SessionTitle from './SessionTitle.vue';

afterEach(() => vi.unstubAllGlobals());
describe('SessionTitle', () => {
  it('scrolls overflowing titles only on hover and resets immediately on leave', async () => {
    const view = mount(SessionTitle, { props: { title: '什么是哈夫曼树？可以给我举一个代码示例吗' } });
    Object.defineProperty(view.element, 'clientWidth', { value: 180 });
    Object.defineProperty(view.get('.session-title__text').element, 'scrollWidth', { value: 400 });
    await view.trigger('mouseenter');
    expect(view.get('.session-title__text').classes()).toContain('is-scrolling');
    expect(view.get('.session-title__text').attributes('style')).toContain('-220px');
    await view.trigger('mouseleave');
    expect(view.get('.session-title__text').classes()).not.toContain('is-scrolling');
    expect(view.text()).toBe(view.props('title'));
    view.unmount();
  });
  it('keeps short titles and reduced-motion titles still', async () => {
    const view = mount(SessionTitle, { props: { title: '栈' } });
    await view.trigger('mouseenter');
    expect(view.get('.session-title__text').classes()).not.toContain('is-scrolling');
    Object.defineProperty(view.element, 'clientWidth', { value: 100 });
    Object.defineProperty(view.get('.session-title__text').element, 'scrollWidth', { value: 300 });
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    await view.trigger('mouseenter');
    expect(view.get('.session-title__text').classes()).not.toContain('is-scrolling');
    view.unmount();
  });
});
