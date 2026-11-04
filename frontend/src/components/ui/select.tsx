import * as SelectPrimitive from '@radix-ui/react-select'
import { Check, ChevronDown } from 'lucide-react'
import type { Ref } from 'react'

export function Select({ id, value, onValueChange, onBlur, name, disabled, options, ref }: {
  id: string
  value: string
  onValueChange: (value: string) => void
  onBlur?: () => void
  name?: string
  disabled?: boolean
  options: { value: string; label: string }[]
  ref?: Ref<HTMLButtonElement>
}) {
  return (
    <SelectPrimitive.Root value={value} onValueChange={onValueChange} name={name} disabled={disabled}>
      <SelectPrimitive.Trigger id={id} ref={ref} onBlur={onBlur} className="select-trigger">
        <SelectPrimitive.Value />
        <SelectPrimitive.Icon asChild><ChevronDown size={16} /></SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>
      <SelectPrimitive.Portal>
        <SelectPrimitive.Content className="select-menu" position="popper" sideOffset={6} collisionPadding={12}>
          <SelectPrimitive.Viewport className="select-options">
            {options.map((option) => (
              <SelectPrimitive.Item key={option.value} value={option.value} className="select-option">
                <SelectPrimitive.ItemText>{option.label}</SelectPrimitive.ItemText>
                <SelectPrimitive.ItemIndicator><Check size={15} /></SelectPrimitive.ItemIndicator>
              </SelectPrimitive.Item>
            ))}
          </SelectPrimitive.Viewport>
        </SelectPrimitive.Content>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  )
}
