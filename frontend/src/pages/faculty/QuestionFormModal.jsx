/* eslint-disable react/prop-types */
import {
  BookOpen,
  CircleHelp,
  ImagePlus,
  ListChecks,
  Plus,
  Trash2,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import SecureImage from '../../components/common/SecureImage';
import {
  Button,
  FormField,
  Input,
  Modal,
  Select,
  Textarea,
} from '../../components/ui';
import { applyServerFieldErrors, axiosInstance, errorMessage, responseData } from '../admin/adminPageUtils';

const QUESTION_TYPES = [
  { value: 'MCQ', label: 'Multiple choice', description: 'Choose one correct option.', icon: ListChecks },
  { value: 'TRUE_FALSE', label: 'True or False', description: 'A statement with two choices.', icon: CircleHelp },
  { value: 'SHORT_ANSWER', label: 'Short answer', description: 'A written response from students.', icon: BookOpen },
];
const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'];
const MAX_IMAGE_SIZE = 2 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

function initialOptions(question, type) {
  if (type === 'TRUE_FALSE') {
    const correctText = question?.options?.find((option) => option.isCorrect)?.optionText?.toLowerCase();
    return [
      { optionKey: 'A', optionText: 'True', isCorrect: correctText === 'true', sortOrder: 0 },
      { optionKey: 'B', optionText: 'False', isCorrect: correctText === 'false', sortOrder: 1 },
    ];
  }
  if (type === 'MCQ' && question?.options?.length) {
    return [...question.options]
      .sort((left, right) => left.sortOrder - right.sortOrder)
      .map((option, index) => ({
        optionKey: option.optionKey || String.fromCharCode(65 + index),
        optionText: option.optionText ?? '',
        isCorrect: Boolean(option.isCorrect),
        sortOrder: index,
      }));
  }
  return type === 'MCQ'
    ? [
      { optionKey: 'A', optionText: '', isCorrect: true, sortOrder: 0 },
      { optionKey: 'B', optionText: '', isCorrect: false, sortOrder: 1 },
    ]
    : [];
}

function initialValues(question) {
  return {
    text: question?.text ?? '',
    type: question?.type ?? 'MCQ',
    subjectId: question?.subjectId == null ? '' : String(question.subjectId),
    classLevel: question?.classLevel ?? '',
    topic: question?.topic ?? '',
    difficulty: question?.difficulty ?? 'MEDIUM',
    marks: question?.marks ?? 1,
    explanation: question?.explanation ?? '',
    modelAnswer: question?.modelAnswer ?? '',
    options: initialOptions(question, question?.type ?? 'MCQ'),
  };
}

function optionKey(index) {
  return String.fromCharCode(65 + index);
}

function validateImage(file) {
  if (!file) return 'Choose an image to upload.';
  if (!ALLOWED_IMAGE_TYPES.includes(file.type)) return 'Image must be JPEG, PNG, or WebP.';
  if (file.size > MAX_IMAGE_SIZE) return 'Image must not exceed 2 MB.';
  return '';
}

function QuestionTypeChoice({ type, currentType, onChange, disabled }) {
  return (
    <div>
      <p className="mb-2 text-sm font-semibold text-app-text">Question type</p>
      <div className="grid gap-2 sm:grid-cols-3">
        {QUESTION_TYPES.map(({ value, label, description, icon: Icon }) => {
          const selected = currentType === value;
          return (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={disabled || type !== 'new'}
              onClick={() => onChange(value)}
              className={`flex min-h-24 items-start gap-3 rounded-2xl border p-3 text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-default ${selected ? 'border-accent bg-accent/10 text-accent ring-1 ring-accent/20' : 'border-border bg-card text-muted hover:border-accent/40'}`}
            >
              <Icon aria-hidden="true" size={19} className="mt-0.5 shrink-0" />
              <span>
                <span className="block text-sm font-bold">{label}</span>
                <span className="mt-1 block text-xs leading-relaxed">{description}</span>
              </span>
            </button>
          );
        })}
      </div>
      {type !== 'new' && <p className="mt-2 text-xs text-muted">Question type cannot be changed after creation.</p>}
    </div>
  );
}

function StudentPreview({ values, imageFile, imageUrl }) {
  const previewImage = imageFile ? { file: imageFile } : imageUrl ? { src: imageUrl } : null;
  return (
    <aside aria-label="Student preview" className="rounded-2xl border border-accent/20 bg-accent/5 p-4 sm:p-5">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="font-bold text-app-text">Student preview</h3>
        <span className="rounded-full bg-card px-2.5 py-1 text-xs font-semibold text-accent">Preview</span>
      </div>
      {previewImage && (
        <SecureImage
          {...previewImage}
          alt="Question illustration"
          className="mb-3 h-36 w-full"
          imageClassName="h-full w-full object-contain"
        />
      )}
      <p className="whitespace-pre-wrap break-words text-sm font-semibold leading-relaxed text-app-text">
        {values.text || 'Your question will appear here.'}
      </p>
      {(values.type === 'MCQ' || values.type === 'TRUE_FALSE') && (
        <div className="mt-4 space-y-2">
          {(values.options ?? []).map((option, index) => (
            <div key={`${option.optionKey}-${index}`} className="flex items-center gap-2 rounded-xl border border-border bg-card px-3 py-2.5 text-sm text-app-text">
              <span aria-hidden="true" className="h-4 w-4 shrink-0 rounded-full border border-muted/50" />
              <span className="break-words">{option.optionText || `Option ${index + 1}`}</span>
            </div>
          ))}
        </div>
      )}
      <p className="mt-4 text-xs font-medium text-muted">{values.marks || 1} {Number(values.marks) === 1 ? 'mark' : 'marks'}</p>
    </aside>
  );
}

export default function QuestionFormModal({
  open,
  onClose,
  onSaved,
  assignments,
  question = null,
}) {
  const editing = Boolean(question);
  const [imageFile, setImageFile] = useState(null);
  const [removeExistingImage, setRemoveExistingImage] = useState(false);
  const [pendingImageOperation, setPendingImageOperation] = useState(null);
  const [saving, setSaving] = useState(false);
  const form = useForm({ defaultValues: initialValues(question) });
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    clearErrors,
    reset,
    formState: { errors },
  } = form;
  const values = watch();
  const selectedSubject = String(values.subjectId ?? '');
  const currentType = values.type ?? 'MCQ';

  const subjectOptions = useMemo(() => {
    const unique = new Map();
    assignments.forEach((assignment) => {
      if (assignment.subjectId != null) unique.set(String(assignment.subjectId), assignment.subjectName);
    });
    return [...unique.entries()].sort((left, right) => String(left[1]).localeCompare(String(right[1])));
  }, [assignments]);

  const classOptions = useMemo(() => {
    const relevant = selectedSubject
      ? assignments.filter((assignment) => String(assignment.subjectId) === selectedSubject)
      : assignments;
    const unique = new Map();
    relevant.forEach((assignment) => {
      if (assignment.className) unique.set(assignment.className, assignment.className);
    });
    if (question?.classLevel && !unique.has(question.classLevel) && selectedSubject === String(question.subjectId)) {
      unique.set(question.classLevel, question.classLevel);
    }
    return [...unique.keys()].sort((left, right) => left.localeCompare(right));
  }, [assignments, question, selectedSubject]);

  useEffect(() => {
    if (!open) return;
    reset(initialValues(question));
    setImageFile(null);
    setRemoveExistingImage(false);
    setPendingImageOperation(null);
  }, [open, question, reset]);

  function changeType(nextType) {
    setValue('type', nextType, { shouldDirty: true, shouldValidate: true });
    setValue('options', initialOptions(null, nextType), { shouldDirty: true, shouldValidate: true });
    clearErrors('options');
  }

  function selectCorrectOption(index) {
    const next = (values.options ?? []).map((option, optionIndex) => ({
      ...option,
      isCorrect: optionIndex === index,
    }));
    setValue('options', next, { shouldDirty: true, shouldValidate: true });
    clearErrors('options');
  }

  function updateOption(index, text) {
    const next = (values.options ?? []).map((option, optionIndex) => (
      optionIndex === index ? { ...option, optionText: text } : option
    ));
    setValue('options', next, { shouldDirty: true });
    clearErrors(`options.${index}.optionText`);
    clearErrors('options');
  }

  function addOption() {
    const options = values.options ?? [];
    if (options.length >= 6) return;
    setValue('options', [
      ...options,
      { optionKey: optionKey(options.length), optionText: '', isCorrect: false, sortOrder: options.length },
    ], { shouldDirty: true });
    clearErrors('options');
  }

  function removeOption(index) {
    const next = (values.options ?? [])
      .filter((_, optionIndex) => optionIndex !== index)
      .map((option, optionIndex) => ({
        ...option,
        optionKey: optionKey(optionIndex),
        sortOrder: optionIndex,
        isCorrect: option.isCorrect || (optionIndex === 0 && !(values.options ?? []).some((entry) => entry.isCorrect)),
      }));
    setValue('options', next, { shouldDirty: true });
    clearErrors('options');
  }

  function handleImageSelection(event) {
    const file = event.target.files?.[0] ?? null;
    event.target.value = '';
    if (!file) return;
    const issue = validateImage(file);
    if (issue) {
      setError('image', { type: 'validate', message: issue });
      toast.error(issue);
      return;
    }
    clearErrors('image');
    setImageFile(file);
    setRemoveExistingImage(false);
  }

  function removeImage() {
    if (imageFile) {
      setImageFile(null);
      clearErrors('image');
    } else if (question?.imageUrl) {
      setRemoveExistingImage(true);
    }
  }

  async function executeImageOperation(operation) {
    if (operation.kind === 'upload') {
      const body = new FormData();
      body.append('image', operation.file);
      const response = await axiosInstance.post(`/faculty/questions/${operation.questionId}/image`, body);
      return responseData(response);
    } else {
      const response = await axiosInstance.delete(`/faculty/questions/${operation.questionId}/image`);
      return responseData(response);
    }
  }

  async function retryPendingImageOperation() {
    if (!pendingImageOperation) return;
    setSaving(true);
    try {
      const savedQuestion = await executeImageOperation(pendingImageOperation);
      toast.success(pendingImageOperation.kind === 'upload' ? 'Question image uploaded' : 'Question image removed');
      setPendingImageOperation(null);
      await onSaved(savedQuestion);
      onClose();
    } catch (requestError) {
      const message = errorMessage(requestError);
      toast.error(`The question is saved, but the image change failed: ${message}`);
    } finally {
      setSaving(false);
    }
  }

  function validateOptions() {
    if (currentType === 'SHORT_ANSWER') return true;
    const options = values.options ?? [];
    const correctCount = options.filter((option) => option.isCorrect).length;
    const validCount = currentType === 'MCQ'
      ? options.length >= 2 && options.length <= 6
      : options.length === 2
        && options.map((option) => option.optionText.trim().toLowerCase()).sort().join('|') === 'false|true';
    const emptyOption = options.findIndex((option) => !option.optionText.trim());
    if (emptyOption >= 0) {
      setError(`options.${emptyOption}.optionText`, { type: 'validate', message: 'Enter the option text.' });
      return false;
    }
    if (!validCount || correctCount !== 1) {
      setError('options', {
        type: 'validate',
        message: currentType === 'MCQ'
          ? 'Add 2 to 6 options and select exactly one correct answer.'
          : 'True/False questions require both options and exactly one correct answer.',
      });
      return false;
    }
    return true;
  }

  async function saveQuestion(formValues) {
    if (pendingImageOperation) {
      await retryPendingImageOperation();
      return;
    }
    if (!validateOptions()) return;
    if (imageFile) {
      const imageError = validateImage(imageFile);
      if (imageError) {
        setError('image', { type: 'validate', message: imageError });
        toast.error(imageError);
        return;
      }
    }
    const request = {
      text: formValues.text,
      type: formValues.type,
      subjectId: Number(formValues.subjectId),
      classLevel: formValues.classLevel,
      topic: formValues.topic || null,
      difficulty: formValues.difficulty,
      marks: Number(formValues.marks),
      explanation: formValues.explanation || null,
      modelAnswer: formValues.type === 'SHORT_ANSWER' ? (formValues.modelAnswer || null) : null,
      options: formValues.type === 'SHORT_ANSWER'
        ? []
        : (formValues.options ?? []).map((option, index) => ({
          optionKey: option.optionKey || optionKey(index),
          optionText: option.optionText,
          isCorrect: Boolean(option.isCorrect),
          sortOrder: index,
        })),
      removeImage: false,
    };

    setSaving(true);
    try {
      const response = editing
        ? await axiosInstance.put(`/faculty/questions/${question.id}`, request)
        : await axiosInstance.post('/faculty/questions', request);
      const savedQuestion = responseData(response);
      if (!savedQuestion?.id) throw new Error('The server did not return the saved question.');

      let imageOperation = null;
      if (imageFile) {
        imageOperation = { kind: 'upload', file: imageFile, questionId: savedQuestion.id };
      } else if (editing && removeExistingImage && question.imageUrl) {
        imageOperation = { kind: 'delete', questionId: savedQuestion.id };
      }

      if (imageOperation) {
        try {
          const updatedQuestion = await executeImageOperation(imageOperation);
          await onSaved(updatedQuestion ?? savedQuestion);
        } catch (imageError) {
          setPendingImageOperation(imageOperation);
          toast.error(`Question saved, but the image change failed. Use Retry image change to try again: ${errorMessage(imageError)}`);
          return;
        }
      } else {
        await onSaved(savedQuestion);
      }

      toast.success(editing ? 'Question updated' : 'Question created');
      onClose();
    } catch (requestError) {
      applyServerFieldErrors(requestError, setError);
      toast.error(errorMessage(requestError, 'Could not save the question.'));
    } finally {
      setSaving(false);
    }
  }

  function requestClose() {
    if (saving) return;
    if (pendingImageOperation) {
      toast.error('Retry the image change before closing this saved question.');
      return;
    }
    onClose();
  }

  const previewImageUrl = removeExistingImage ? null : question?.imageUrl;
  const currentImage = imageFile || previewImageUrl;
  const formDisabled = saving || Boolean(pendingImageOperation);
  const options = values.options ?? [];

  return (
    <Modal
      open={open}
      onClose={requestClose}
      title={editing ? 'Edit question' : 'New question'}
      closeOnOutsideClick={!saving && !pendingImageOperation}
      className="!max-w-6xl max-h-[calc(100dvh-1rem)] overflow-y-auto p-4 sm:p-6"
    >
      <form onSubmit={handleSubmit(saveQuestion)} noValidate>
        {pendingImageOperation && (
          <div role="alert" className="mb-4 flex flex-col gap-3 rounded-2xl border border-warning/30 bg-warning/10 p-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm font-semibold text-app-text">
              The question was saved, but its image change is still pending. Retry before closing.
            </p>
            <Button type="button" size="sm" onClick={retryPendingImageOperation} loading={saving}>
              Retry image {pendingImageOperation.kind === 'upload' ? 'upload' : 'removal'}
            </Button>
          </div>
        )}

        <fieldset disabled={formDisabled} className="space-y-5 disabled:opacity-75">
          <QuestionTypeChoice
            type={editing ? 'edit' : 'new'}
            currentType={currentType}
            onChange={changeType}
            disabled={formDisabled}
          />
          <FormField error={errors.type?.message}>
            <input type="hidden" {...register('type', { required: 'Choose a question type.' })} />
          </FormField>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <FormField label="Subject" required error={errors.subjectId?.message}>
              <Select
                {...register('subjectId', { required: 'Select a subject.' })}
                invalid={Boolean(errors.subjectId)}
                onChange={(event) => {
                  setValue('subjectId', event.target.value, { shouldValidate: true });
                  setValue('classLevel', '', { shouldValidate: true });
                  clearErrors('classLevel');
                }}
              >
                <option value="">Select a subject</option>
                {subjectOptions.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
              </Select>
            </FormField>
            <FormField label="Class level" required error={errors.classLevel?.message}>
              <Select
                {...register('classLevel', {
                  required: 'Select a class level.',
                  maxLength: { value: 20, message: 'Class level must not exceed 20 characters.' },
                })}
                invalid={Boolean(errors.classLevel)}
              >
                <option value="">Select a class</option>
                {classOptions.map((classLevel) => <option key={classLevel} value={classLevel}>{classLevel}</option>)}
              </Select>
            </FormField>
            <FormField label="Difficulty" required error={errors.difficulty?.message}>
              <Select {...register('difficulty', { required: 'Select a difficulty.' })} invalid={Boolean(errors.difficulty)}>
                {DIFFICULTIES.map((difficulty) => <option key={difficulty} value={difficulty}>{difficulty.charAt(0) + difficulty.slice(1).toLowerCase()}</option>)}
              </Select>
            </FormField>
            <FormField label="Marks" required error={errors.marks?.message}>
              <Input
                type="number"
                min="1"
                step="1"
                {...register('marks', {
                  valueAsNumber: true,
                  required: 'Marks are required.',
                  validate: (value) => (Number.isInteger(value) && value > 0) || 'Marks must be a positive whole number.',
                })}
                invalid={Boolean(errors.marks)}
              />
            </FormField>
          </div>
          {assignments.length === 0 && (
            <p role="alert" className="text-sm font-semibold text-error">No faculty assignments are available. Ask an administrator to assign your class and subject.</p>
          )}

          <div className="grid gap-4 lg:grid-cols-[minmax(0,1.35fr)_minmax(18rem,0.85fr)]">
            <div className="space-y-4">
              <FormField label="Question text" required error={errors.text?.message}>
                <Textarea
                  rows={4}
                  placeholder="Write the question students will answer"
                  {...register('text', {
                    validate: (value) => value.trim().length > 0 || 'Question text is required.',
                  })}
                  invalid={Boolean(errors.text)}
                />
              </FormField>

              <div className="grid gap-4 sm:grid-cols-2">
                <FormField label="Topic" error={errors.topic?.message}>
                  <Input
                    maxLength={100}
                    placeholder="Optional topic"
                    {...register('topic', { maxLength: { value: 100, message: 'Topic must not exceed 100 characters.' } })}
                    invalid={Boolean(errors.topic)}
                  />
                </FormField>
                <FormField label="Image" error={errors.image?.message}>
                  <div className="flex flex-wrap items-center gap-2">
                    <label className="inline-flex min-h-12 cursor-pointer items-center gap-2 rounded-xl border border-border bg-card px-4 py-2 text-sm font-semibold text-app-text hover:bg-bg">
                      <ImagePlus aria-hidden="true" size={18} />
                      {currentImage ? 'Replace image' : 'Choose image'}
                      <input
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        onChange={handleImageSelection}
                        className="sr-only"
                        aria-label="Choose question image"
                        aria-invalid={Boolean(errors.image)}
                      />
                    </label>
                    {currentImage && (
                      <Button type="button" variant="ghost" size="sm" onClick={removeImage} aria-label="Remove question image">
                        <Trash2 aria-hidden="true" size={16} />
                        Remove
                      </Button>
                    )}
                    {removeExistingImage && (
                      <Button type="button" variant="ghost" size="sm" onClick={() => setRemoveExistingImage(false)}>
                        Keep current image
                      </Button>
                    )}
                  </div>
                  <p className="mt-1 text-xs text-muted">JPEG, PNG, or WebP. Maximum 2 MB.</p>
                </FormField>
              </div>

              {currentImage && (
                <div className="max-w-sm">
                  <SecureImage
                    src={imageFile ? undefined : previewImageUrl}
                    file={imageFile ?? undefined}
                    alt="Question image preview"
                    className="h-40 w-full"
                    imageClassName="h-full w-full object-contain"
                  />
                </div>
              )}

              {(currentType === 'MCQ' || currentType === 'TRUE_FALSE') && (
                <section aria-labelledby="options-heading" className="rounded-2xl border border-border bg-bg/60 p-4">
                  <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <h3 id="options-heading" className="font-bold text-app-text">
                        {currentType === 'MCQ' ? 'Answer options' : 'Correct answer'}
                      </h3>
                      {currentType === 'MCQ' && <p className="mt-1 text-xs text-muted">Select one correct option.</p>}
                    </div>
                    {currentType === 'MCQ' && (
                      <Button type="button" variant="secondary" size="sm" onClick={addOption} disabled={options.length >= 6}>
                        <Plus aria-hidden="true" size={16} />
                        Add option
                      </Button>
                    )}
                  </div>
                  <div className="space-y-2">
                    {options.map((option, index) => (
                      <div key={`${option.optionKey}-${index}`}>
                        <div className="flex items-start gap-2">
                          <label className="flex min-h-14 w-full min-w-0 items-center gap-3 rounded-xl border border-border bg-card px-3">
                            <input
                              type="radio"
                              name="correctOption"
                              checked={Boolean(option.isCorrect)}
                              onChange={() => selectCorrectOption(index)}
                              aria-label={`Mark option ${index + 1} as correct`}
                              className="h-4 w-4 accent-accent"
                            />
                            {currentType === 'TRUE_FALSE' ? (
                              <span className="flex-1 py-3 text-sm font-semibold text-app-text">{option.optionText}</span>
                            ) : (
                              <Input
                                value={option.optionText}
                                onChange={(event) => updateOption(index, event.target.value)}
                                aria-label={`Option ${index + 1} text`}
                                placeholder={`Option ${index + 1}`}
                                className="min-h-11 border-0 px-1 py-2 shadow-none focus:ring-0"
                                invalid={Boolean(errors.options?.[index]?.optionText)}
                              />
                            )}
                          </label>
                          {currentType === 'MCQ' && options.length > 2 && (
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              onClick={() => removeOption(index)}
                              aria-label={`Remove option ${index + 1}`}
                              className="min-w-11 px-2"
                            >
                              <Trash2 aria-hidden="true" size={17} />
                            </Button>
                          )}
                        </div>
                        {errors.options?.[index]?.optionText?.message && (
                          <p role="alert" className="mt-1 text-sm font-semibold text-error">
                            {errors.options[index].optionText.message}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                  {errors.options?.message && <p role="alert" className="mt-2 text-sm font-semibold text-error">{errors.options.message}</p>}
                </section>
              )}

              <FormField label="Explanation" error={errors.explanation?.message}>
                <Textarea
                  rows={3}
                  placeholder="Optional explanation shown with the answer"
                  {...register('explanation')}
                  invalid={Boolean(errors.explanation)}
                />
              </FormField>

              {currentType === 'SHORT_ANSWER' && (
                <FormField label="Model answer or expected keywords" error={errors.modelAnswer?.message}>
                  <Textarea
                    rows={3}
                    placeholder="Optional model answer or grading keywords"
                    {...register('modelAnswer')}
                    invalid={Boolean(errors.modelAnswer)}
                  />
                </FormField>
              )}
            </div>

            <StudentPreview
              values={values}
              imageFile={imageFile}
              imageUrl={previewImageUrl}
            />
          </div>
        </fieldset>

        <div className="mt-5 flex flex-col-reverse justify-end gap-3 border-t border-border pt-4 sm:flex-row">
          <Button type="button" variant="ghost" onClick={requestClose} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" loading={saving} disabled={Boolean(pendingImageOperation)}>
            {pendingImageOperation ? 'Retry image change' : editing ? 'Save changes' : 'Create question'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
