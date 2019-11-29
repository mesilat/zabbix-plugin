import _ from 'underscore';
import { getSpace } from '../components/util';

export default async (spaceKey, space) => {
  const response = await getSpace(spaceKey);
  expect(response.status).toBe(200);
  if (response.status === 200 && response.data.results.length > 0){
    console.log('123', response.data.results[0]);
    _.extend(space, response.data.results[0]);
  }
};
